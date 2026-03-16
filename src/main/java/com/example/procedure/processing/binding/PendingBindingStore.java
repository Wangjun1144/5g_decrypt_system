package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 待绑定缓冲状态存储。
 *
 * 职责：
 * 1. 暂存无法立即确定 ueId 的消息
 * 2. 维护“未绑定索引队列”
 * 3. 维护“ueId 先到，索引后到”的等待队列
 * 4. 提供 flush 所需的状态访问
 *
 * 当前阶段说明：
 * - 仍然是进程内内存结构
 * - 先完整保留原 UeIdBinder 的行为
 * - 后续阶段 3 再抽象成 BindingRepository / PendingBindingRepository
 */
@Component
public class PendingBindingStore {

    private static final Duration PENDING_TTL = Duration.ofSeconds(120);

    /** 按 ngapId 缓冲的历史消息 */
    private final Map<String, List<PendingMsg>> pendingByNgapId = new ConcurrentHashMap<>();

    /** 按 rntiType 缓冲的历史消息 */
    private final Map<String, List<PendingMsg>> pendingByRntiType = new ConcurrentHashMap<>();

    /** 尚未绑定的 ngapId 队列（用于“就近绑定”） */
    private final Deque<String> unboundNgapIds = new ConcurrentLinkedDeque<>();

    /** 尚未绑定的 rntiType 队列（用于“就近绑定”） */
    private final Deque<String> unboundRntiTypes = new ConcurrentLinkedDeque<>();

    /** 防止同一个索引重复入队 */
    private final Set<String> queuedNgapIds = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedRntiTypes = ConcurrentHashMap.newKeySet();

    /** ueId 已到，但还在等 ngapId */
    private final Deque<String> ueWaitNgap = new ConcurrentLinkedDeque<>();

    /** ueId 已到，但还在等 rntiType */
    private final Deque<String> ueWaitRntiType = new ConcurrentLinkedDeque<>();

    /** 防重复入队 */
    private final Set<String> queuedUeWaitNgap = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedUeWaitRnti = ConcurrentHashMap.newKeySet();

    public void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long expireBefore = now - PENDING_TTL.toMillis();

        cleanupMap(pendingByNgapId, expireBefore);
        cleanupMap(pendingByRntiType, expireBefore);
    }

    /**
     * 当前消息无法确定 ueId 时，按索引缓冲。
     *
     * 规则保持原逻辑：
     * - 优先按 ngapId 缓冲
     * - 否则按 rntiType 缓冲
     * - 若两者都没有，则无法缓冲
     */
    public BufferDecision buffer(SignalingMessage msg, String ngapId, String rntiType) {
        long now = System.currentTimeMillis();

        if (!isEmpty(ngapId)) {
            pendingByNgapId.computeIfAbsent(ngapId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new PendingMsg(msg, now));
            enqueueNgapOnce(ngapId);
            return BufferDecision.bufferedByNgap(ngapId);
        }

        if (!isEmpty(rntiType)) {
            pendingByRntiType.computeIfAbsent(rntiType, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new PendingMsg(msg, now));
            enqueueRntiOnce(rntiType);
            return BufferDecision.bufferedByRnti(rntiType);
        }

        return BufferDecision.notBufferable();
    }

    public void ensureUeInWaitQueuesIfNeeded(
            String ueId,
            boolean ueNgapUnbound,
            boolean ueRntiUnbound
    ) {
        if (isEmpty(ueId)) {
            return;
        }

        if (ueNgapUnbound && queuedUeWaitNgap.add(ueId)) {
            ueWaitNgap.offerLast(ueId);
        }

        if (ueRntiUnbound && queuedUeWaitRnti.add(ueId)) {
            ueWaitRntiType.offerLast(ueId);
        }
    }

    public String peekFirstWaitingUeForNgap() {
        return ueWaitNgap.peekFirst();
    }

    public String pollFirstWaitingUeForNgap() {
        String ueId = ueWaitNgap.pollFirst();
        if (ueId != null) {
            queuedUeWaitNgap.remove(ueId);
        }
        return ueId;
    }

    public String peekFirstWaitingUeForRnti() {
        return ueWaitRntiType.peekFirst();
    }

    public String pollFirstWaitingUeForRnti() {
        String ueId = ueWaitRntiType.pollFirst();
        if (ueId != null) {
            queuedUeWaitRnti.remove(ueId);
        }
        return ueId;
    }

    public void removeUeWaitNgap(String ueId) {
        queuedUeWaitNgap.remove(ueId);
    }

    public void removeUeWaitRnti(String ueId) {
        queuedUeWaitRnti.remove(ueId);
    }

    public String pollFirstQueuedNgapCandidate() {
        return unboundNgapIds.pollFirst();
    }

    public void markNgapDequeued(String ngapId) {
        queuedNgapIds.remove(ngapId);
    }

    public String pollFirstQueuedRntiCandidate() {
        return unboundRntiTypes.pollFirst();
    }

    public void markRntiDequeued(String rntiType) {
        queuedRntiTypes.remove(rntiType);
    }

    public List<SignalingMessage> releaseNgapPending(String ngapId, String ueId) {
        List<PendingMsg> list = pendingByNgapId.remove(ngapId);
        if (list == null || list.isEmpty()) {
            queuedNgapIds.remove(ngapId);
            return List.of();
        }

        list.sort(Comparator.comparingLong(p -> p.ts));

        List<SignalingMessage> result = new ArrayList<>(list.size());
        for (PendingMsg pendingMsg : list) {
            pendingMsg.msg.setUeId(ueId);
            result.add(pendingMsg.msg);
        }

        queuedNgapIds.remove(ngapId);
        return result;
    }

    public List<SignalingMessage> releaseRntiPending(String rntiType, String ueId) {
        List<PendingMsg> list = pendingByRntiType.remove(rntiType);
        if (list == null || list.isEmpty()) {
            queuedRntiTypes.remove(rntiType);
            return List.of();
        }

        list.sort(Comparator.comparingLong(p -> p.ts));

        List<SignalingMessage> result = new ArrayList<>(list.size());
        for (PendingMsg pendingMsg : list) {
            pendingMsg.msg.setUeId(ueId);
            result.add(pendingMsg.msg);
        }

        queuedRntiTypes.remove(rntiType);
        return result;
    }

    private void enqueueNgapOnce(String ngapId) {
        if (!isEmpty(ngapId) && queuedNgapIds.add(ngapId)) {
            unboundNgapIds.offerLast(ngapId);
        }
    }

    private void enqueueRntiOnce(String rntiType) {
        if (!isEmpty(rntiType) && queuedRntiTypes.add(rntiType)) {
            unboundRntiTypes.offerLast(rntiType);
        }
    }

    private void cleanupMap(Map<String, List<PendingMsg>> map, long expireBefore) {
        for (Iterator<Map.Entry<String, List<PendingMsg>>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<PendingMsg>> entry = it.next();
            List<PendingMsg> list = entry.getValue();

            if (list == null || list.isEmpty()) {
                it.remove();
                continue;
            }

            synchronized (list) {
                list.removeIf(p -> p.ts < expireBefore);
            }

            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static class PendingMsg {
        final SignalingMessage msg;
        final long ts;

        PendingMsg(SignalingMessage msg, long ts) {
            this.msg = msg;
            this.ts = ts;
        }
    }

    /**
     * 记录本轮 buffer 的决策结果。
     */
    public static class BufferDecision {
        private final boolean buffered;
        private final String bufferedNgapId;
        private final String bufferedRntiType;

        private BufferDecision(boolean buffered, String bufferedNgapId, String bufferedRntiType) {
            this.buffered = buffered;
            this.bufferedNgapId = bufferedNgapId;
            this.bufferedRntiType = bufferedRntiType;
        }

        public static BufferDecision bufferedByNgap(String ngapId) {
            return new BufferDecision(true, ngapId, null);
        }

        public static BufferDecision bufferedByRnti(String rntiType) {
            return new BufferDecision(true, null, rntiType);
        }

        public static BufferDecision notBufferable() {
            return new BufferDecision(false, null, null);
        }

        public boolean isBuffered() {
            return buffered;
        }

        public String getBufferedNgapId() {
            return bufferedNgapId;
        }

        public String getBufferedRntiType() {
            return bufferedRntiType;
        }
    }
}