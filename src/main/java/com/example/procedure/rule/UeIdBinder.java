package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.MacInfo;
import com.example.procedure.parser.NgapInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;


@Service
public class UeIdBinder {

    // ========= Redis key 前缀 =========
    private static final String MAP_RAN_UE_KEY_PREFIX         = "ue:map:ran:";         // ngapId -> ueId
    private static final String MAP_RNTI_TYPE_UE_KEY_PREFIX   = "ue:map:rntiType:";    // rntiType -> ueId

    // 反向索引：ueId -> ngapId / rntiType（用于判断是否已绑定）
    private static final String UE_IDX_PREFIX = "ue:idx:ue:"; // ue:idx:ue:<ueId>:ran / :rntiType

    private static final Duration REDIS_TTL   = Duration.ofHours(1);

    // pending 缓冲最大等待时间（防止内存增长）
    private static final Duration PENDING_TTL = Duration.ofSeconds(120); // 调试可改大 / 或加开关

    private final StringRedisTemplate redisTemplate;

    public UeIdBinder(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ========= pending：按索引分桶 =========
    private final Map<String, List<PendingMsg>> pendingByNgapId = new ConcurrentHashMap<>();
    private final Map<String, List<PendingMsg>> pendingByRntiType = new ConcurrentHashMap<>();

    // ========= 未绑定索引队列（就近原则） =========
    private final Deque<String> unboundNgapIds = new ConcurrentLinkedDeque<>();
    private final Deque<String> unboundRntiTypes = new ConcurrentLinkedDeque<>();

    // 防重复入队
    private final Set<String> queuedNgapIds = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedRntiTypes = ConcurrentHashMap.newKeySet();

    // ========= 未完成绑定的 UE 队列（支持 ueId 先到） =========
    private final Deque<String> ueWaitNgap = new ConcurrentLinkedDeque<>();
    private final Deque<String> ueWaitRntiType = new ConcurrentLinkedDeque<>();
    private final Set<String> queuedUeWaitNgap = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedUeWaitRnti = ConcurrentHashMap.newKeySet();

    // ========= 本地 cache（加速 lookup） =========
    private final Map<String, String> ngapToUeCache = new ConcurrentHashMap<>();
    private final Map<String, String> rntiTypeToUeCache = new ConcurrentHashMap<>();

    // ========= 入口：处理单条消息 =========
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        cleanupExpiredPending();

        String ueId = normalize(msg.getUeId());
        String ngapId = extractRanUeNgapId(msg);
        String rntiType = extractRntiType(msg);

        // 1) 优先确定 ueId：消息自带 > ngap反查 > rntiType反查
        if (isEmpty(ueId)) ueId = lookupUeIdByNgapId(ngapId);
        if (isEmpty(ueId)) ueId = lookupUeIdByRntiType(rntiType);

        // 2) 仍不能确定 ueId => 缓冲（不进入 downstream）
        if (isEmpty(ueId)) {
            buffer(msg, ngapId, rntiType);
            return;
        }

        // 3) 能确定 ueId：先把当前消息补上 ueId
        msg.setUeId(ueId);

        // 4) 确保这个 ueId 进入 “等待绑定队列”（分别等待 ngap / rnti）
        ensureUeInWaitQueuesIfNeeded(ueId);

        // 5) 绑定 ngapId（强绑定优先：消息自带 ngapId）
        boolean boundNgapNow = false;
        if (!isEmpty(ngapId) && isNgapUnbound(ngapId) && isUeNgapUnbound(ueId)) {
            bindNgapIdToUe(ngapId, ueId);
            flushNgapPending(ngapId, ueId, downstream);
            boundNgapNow = true;
        }

        // 6) 绑定 rntiType（强绑定优先：消息自带 rntiType）
        boolean boundRntiNow = false;
        if (!isEmpty(rntiType) && isRntiTypeUnbound(rntiType) && isUeRntiUnbound(ueId)) {
            bindRntiTypeToUe(rntiType, ueId);
            flushRntiPending(rntiType, ueId, downstream);
            boundRntiNow = true;
        }

        // 7) 如果本消息没带索引/或索引已绑定，按“就近原则”从队列拿一个来绑
        //    注意：ngap 与 rntiType 是独立的，可以都绑
        if (!boundNgapNow && isUeNgapUnbound(ueId)) {
            String candidateNgap = pollFirstReallyUnboundNgap();
            if (candidateNgap != null) {
                bindNgapIdToUe(candidateNgap, ueId);
                flushNgapPending(candidateNgap, ueId, downstream);
            }
        }

        if (!boundRntiNow && isUeRntiUnbound(ueId)) {
            String candidateRnti = pollFirstReallyUnboundRntiType();
            if (candidateRnti != null) {
                bindRntiTypeToUe(candidateRnti, ueId);
                flushRntiPending(candidateRnti, ueId, downstream);
            }
        }

        // 8) 当前消息进入正常处理
        downstream.accept(msg);
    }

    // ========= 缓冲：ueId 未确定时 =========
    private void buffer(SignalingMessage msg, String ngapId, String rntiType) {
        long now = System.currentTimeMillis();

        // 优先按 ngapId 缓冲（更稳定）
        if (!isEmpty(ngapId)) {
            pendingByNgapId.computeIfAbsent(ngapId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new PendingMsg(msg, now));
            enqueueNgapOnce(ngapId);

            // 如果 ueId 先到：ngapId 后到，就近绑定到等待 ngap 的 ue
            tryBindIncomingNgapToWaitingUe(ngapId);
            return;
        }

        if (!isEmpty(rntiType)) {
            pendingByRntiType.computeIfAbsent(rntiType, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new PendingMsg(msg, now));
            enqueueRntiOnce(rntiType);

            // 如果 ueId 先到：rntiType 后到，就近绑定到等待 rnti 的 ue
            tryBindIncomingRntiToWaitingUe(rntiType);
            return;
        }

        // 两个索引都没有：无法缓冲就近绑定，建议直接丢弃或打日志（这里默认丢弃）
    }

    // ========= ueId 先到：索引后到的反向绑定 =========
    private void tryBindIncomingNgapToWaitingUe(String ngapId) {
        if (isEmpty(ngapId) || !isNgapUnbound(ngapId)) return;

        while (true) {
            String ueId = ueWaitNgap.peekFirst();
            if (ueId == null) return;

            // 若该 ueId 已经绑定了 ngap，就跳过
            if (!isUeNgapUnbound(ueId)) {
                ueWaitNgap.pollFirst();
                queuedUeWaitNgap.remove(ueId);
                continue;
            }

            // 绑定
            ueWaitNgap.pollFirst();
            queuedUeWaitNgap.remove(ueId);
            bindNgapIdToUe(ngapId, ueId);
            break;
        }
    }

    private void tryBindIncomingRntiToWaitingUe(String rntiType) {
        if (isEmpty(rntiType) || !isRntiTypeUnbound(rntiType)) return;

        while (true) {
            String ueId = ueWaitRntiType.peekFirst();
            if (ueId == null) return;

            if (!isUeRntiUnbound(ueId)) {
                ueWaitRntiType.pollFirst();
                queuedUeWaitRnti.remove(ueId);
                continue;
            }

            ueWaitRntiType.pollFirst();
            queuedUeWaitRnti.remove(ueId);
            bindRntiTypeToUe(rntiType, ueId);
            break;
        }
    }

    // ========= “等待绑定队列”：只对未绑定的部分入队 =========
    private void ensureUeInWaitQueuesIfNeeded(String ueId) {
        if (isEmpty(ueId)) return;

        if (isUeNgapUnbound(ueId) && queuedUeWaitNgap.add(ueId)) {
            ueWaitNgap.offerLast(ueId);
        }
        if (isUeRntiUnbound(ueId) && queuedUeWaitRnti.add(ueId)) {
            ueWaitRntiType.offerLast(ueId);
        }
    }

    // ========= flush：把 pending 消息补 ueId 再下发 =========
    private void flushNgapPending(String ngapId, String ueId, Consumer<SignalingMessage> downstream) {
        List<PendingMsg> list = pendingByNgapId.remove(ngapId);
        if (list == null || list.isEmpty()) return;

        list.sort(Comparator.comparingLong(p -> p.ts));
        for (PendingMsg p : list) {
            p.msg.setUeId(ueId);
            downstream.accept(p.msg);
        }

        queuedNgapIds.remove(ngapId);
    }

    private void flushRntiPending(String rntiType, String ueId, Consumer<SignalingMessage> downstream) {
        List<PendingMsg> list = pendingByRntiType.remove(rntiType);
        if (list == null || list.isEmpty()) return;

        list.sort(Comparator.comparingLong(p -> p.ts));
        for (PendingMsg p : list) {
            p.msg.setUeId(ueId);
            downstream.accept(p.msg);
        }

        queuedRntiTypes.remove(rntiType);
    }

    // ========= bind：写 Redis + cache + 反向索引 =========
    private void bindNgapIdToUe(String ngapId, String ueId) {
        if (isEmpty(ngapId) || isEmpty(ueId)) return;

        ngapToUeCache.put(ngapId, ueId);

        redisTemplate.opsForValue().set(redisKeyForRanMap(ngapId), ueId, REDIS_TTL);
        redisTemplate.opsForValue().set(redisKeyForUeRanIdx(ueId), ngapId, REDIS_TTL);

        queuedNgapIds.remove(ngapId);
        // 该 UE 不再等待 ngap
        queuedUeWaitNgap.remove(ueId);
    }

    private void bindRntiTypeToUe(String rntiType, String ueId) {
        if (isEmpty(rntiType) || isEmpty(ueId)) return;

        rntiTypeToUeCache.put(rntiType, ueId);

        redisTemplate.opsForValue().set(redisKeyForRntiTypeMap(rntiType), ueId, REDIS_TTL);
        redisTemplate.opsForValue().set(redisKeyForUeRntiIdx(ueId), rntiType, REDIS_TTL);

        queuedRntiTypes.remove(rntiType);
        // 该 UE 不再等待 rnti
        queuedUeWaitRnti.remove(ueId);
    }

    // ========= lookup：优先 cache，其次 Redis =========
    private String lookupUeIdByNgapId(String ngapId) {
        if (isEmpty(ngapId)) return null;

        String v = ngapToUeCache.get(ngapId);
        if (!isEmpty(v)) return v;

        v = redisTemplate.opsForValue().get(redisKeyForRanMap(ngapId));
        if (!isEmpty(v)) ngapToUeCache.put(ngapId, v);
        return normalize(v);
    }

    private String lookupUeIdByRntiType(String rntiType) {
        if (isEmpty(rntiType)) return null;

        String v = rntiTypeToUeCache.get(rntiType);
        if (!isEmpty(v)) return v;

        v = redisTemplate.opsForValue().get(redisKeyForRntiTypeMap(rntiType));
        if (!isEmpty(v)) rntiTypeToUeCache.put(rntiType, v);
        return normalize(v);
    }

    // ========= “是否已绑定”判定（关键：用反向索引判断 ueId） =========
    private boolean isUeNgapUnbound(String ueId) {
        if (isEmpty(ueId)) return false;
        String v = redisTemplate.opsForValue().get(redisKeyForUeRanIdx(ueId));
        return isEmpty(v);
    }

    private boolean isUeRntiUnbound(String ueId) {
        if (isEmpty(ueId)) return false;
        String v = redisTemplate.opsForValue().get(redisKeyForUeRntiIdx(ueId));
        return isEmpty(v);
    }

    private boolean isNgapUnbound(String ngapId) {
        if (isEmpty(ngapId)) return false;

        String c = ngapToUeCache.get(ngapId);
        if (!isEmpty(c)) return false;

        String v = redisTemplate.opsForValue().get(redisKeyForRanMap(ngapId));
        if (!isEmpty(v)) {
            ngapToUeCache.put(ngapId, v);
            return false;
        }
        return true;
    }

    private boolean isRntiTypeUnbound(String rntiType) {
        if (isEmpty(rntiType)) return false;

        String c = rntiTypeToUeCache.get(rntiType);
        if (!isEmpty(c)) return false;

        String v = redisTemplate.opsForValue().get(redisKeyForRntiTypeMap(rntiType));
        if (!isEmpty(v)) {
            rntiTypeToUeCache.put(rntiType, v);
            return false;
        }
        return true;
    }

    // ========= “就近队首”获取：跳过已绑定的脏数据 =========
    private void enqueueNgapOnce(String ngapId) {
        if (isEmpty(ngapId)) return;
        if (queuedNgapIds.add(ngapId)) {
            if (isNgapUnbound(ngapId)) unboundNgapIds.offerLast(ngapId);
        }
    }

    private void enqueueRntiOnce(String rntiType) {
        if (isEmpty(rntiType)) return;
        if (queuedRntiTypes.add(rntiType)) {
            if (isRntiTypeUnbound(rntiType)) unboundRntiTypes.offerLast(rntiType);
        }
    }

    private String pollFirstReallyUnboundNgap() {
        while (true) {
            String x = unboundNgapIds.pollFirst();
            if (x == null) return null;
            if (isNgapUnbound(x)) return x;
            queuedNgapIds.remove(x);
        }
    }

    private String pollFirstReallyUnboundRntiType() {
        while (true) {
            String x = unboundRntiTypes.pollFirst();
            if (x == null) return null;
            if (isRntiTypeUnbound(x)) return x;
            queuedRntiTypes.remove(x);
        }
    }

    // ========= pending 清理 =========
    private void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long expireBefore = now - PENDING_TTL.toMillis();
        cleanupMap(pendingByNgapId, expireBefore);
        cleanupMap(pendingByRntiType, expireBefore);
    }

    private void cleanupMap(Map<String, List<PendingMsg>> map, long expireBefore) {
        for (Iterator<Map.Entry<String, List<PendingMsg>>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<PendingMsg>> e = it.next();
            List<PendingMsg> list = e.getValue();
            if (list == null || list.isEmpty()) {
                it.remove();
                continue;
            }
            synchronized (list) {
                list.removeIf(p -> p.ts < expireBefore);
            }
            if (list.isEmpty()) it.remove();
        }
    }

    // ========= 抽取字段（你按你真实结构微调） =========
    private String extractRanUeNgapId(SignalingMessage msg) {
        if (msg == null) return null;
        List<NgapInfo> ngapList = msg.getNgapInfoList();
        if (ngapList == null || ngapList.isEmpty()) return null;
        NgapInfo ngap = ngapList.get(0);
        if (ngap == null) return null;
        return normalize(ngap.getRanUeNgapId());
    }

    private String extractRntiType(SignalingMessage msg) {
        if (msg == null) return null;
        MacInfo mac = msg.getMacInfo();
        if (mac == null) return null;
        // 你说的 mac-nr.rnti-type
        return normalize(mac.getRntiType()); // 如果 getter 名不同，你改这里
    }

    // ========= Redis key 生成 =========
    private String redisKeyForRanMap(String ngapId) {
        return MAP_RAN_UE_KEY_PREFIX + ngapId;
    }

    private String redisKeyForRntiTypeMap(String rntiType) {
        return MAP_RNTI_TYPE_UE_KEY_PREFIX + rntiType;
    }

    private String redisKeyForUeRanIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":ran";
    }

    private String redisKeyForUeRntiIdx(String ueId) {
        return UE_IDX_PREFIX + ueId + ":rntiType";
    }

    // ========= utils =========
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }

    private static class PendingMsg {
        final SignalingMessage msg;
        final long ts;
        PendingMsg(SignalingMessage msg, long ts) {
            this.msg = msg;
            this.ts = ts;
        }
    }
}
