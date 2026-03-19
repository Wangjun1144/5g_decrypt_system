package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 待绑定缓冲状态存储。
 *
 * 当前职责：
 * 1. 缓冲“暂时无法确定 ueId”的消息
 * 2. 维护尚未绑定的 ngapId / rntiType 候选队列
 * 3. 维护“ueId 先到、索引后到”的等待队列
 * 4. 在绑定建立后释放对应 pending 消息
 *
 * 第 14 小步的重构重点：
 * - 不改变现有缓冲语义
 * - 不改变现有 TTL
 * - 不改变当前“优先 ngapId，其次 rntiType”的缓冲策略
 * - 只把内部状态访问整理为更清晰的语义方法
 *
 * 当前阶段的三类核心状态：
 *
 * 一、按索引缓冲的 pending 消息
 * - pendingByNgapId
 * - pendingByRntiType
 *
 * 二、尚未绑定的索引候选队列
 * - unboundNgapIds
 * - unboundRntiTypes
 *
 * 三、ue 先到、索引后到的等待队列
 * - ueWaitNgap
 * - ueWaitRntiType
 */
@Component
public class PendingBindingStore {

    /**
     * 当前待绑定消息的生存时间。
     *
     * 保持你现有实现中的 120 秒，
     * 这一小步不调整生命周期策略。
     */
    private static final Duration PENDING_TTL = Duration.ofSeconds(120);

    /**
     * 按 ngapId 缓冲的历史 pending 消息。
     */
    private final Map<String, List<PendingMsg>> pendingByNgapId = new ConcurrentHashMap<>();

    /**
     * 按 rntiType 缓冲的历史 pending 消息。
     */
    private final Map<String, List<PendingMsg>> pendingByRntiType = new ConcurrentHashMap<>();

    /**
     * 尚未绑定的 ngapId 候选队列。
     *
     * 用途：
     * - 当前消息可以确定 ueId，但没有可直接强绑定的新 ngapId 时，
     *   BindingResolver 会尝试从这里做“就近绑定”。
     */
    private final Deque<String> unboundNgapIds = new ConcurrentLinkedDeque<>();

    /**
     * 尚未绑定的 rntiType 候选队列。
     */
    private final Deque<String> unboundRntiTypes = new ConcurrentLinkedDeque<>();

    /**
     * 防止同一个 ngapId 被重复加入候选队列。
     */
    private final Set<String> queuedNgapIds = ConcurrentHashMap.newKeySet();

    /**
     * 防止同一个 rntiType 被重复加入候选队列。
     */
    private final Set<String> queuedRntiTypes = ConcurrentHashMap.newKeySet();

    /**
     * ueId 已经出现，但仍在等待 ngapId。
     */
    private final Deque<String> ueWaitNgap = new ConcurrentLinkedDeque<>();

    /**
     * ueId 已经出现，但仍在等待 rntiType。
     */
    private final Deque<String> ueWaitRntiType = new ConcurrentLinkedDeque<>();

    /**
     * 防止同一个 ueId 重复进入 ngap 等待队列。
     */
    private final Set<String> queuedUeWaitNgap = ConcurrentHashMap.newKeySet();

    /**
     * 防止同一个 ueId 重复进入 rnti 等待队列。
     */
    private final Set<String> queuedUeWaitRnti = ConcurrentHashMap.newKeySet();

    /**
     * 清理过期 pending。
     *
     * 当前策略保持不变：
     * - 只清理按索引缓冲的 pending 消息
     * - 候选队列与 ue 等待队列不在这里清理
     */
    public void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long expireBefore = now - PENDING_TTL.toMillis();

        cleanupPendingMap(pendingByNgapId, expireBefore);
        cleanupPendingMap(pendingByRntiType, expireBefore);
    }

    /**
     * 当当前消息无法确定 ueId 时，按现有规则进行缓冲。
     *
     * 当前规则保持不变：
     * 1. 优先按 ngapId 缓冲
     * 2. 否则按 rntiType 缓冲
     * 3. 如果两者都没有，则本轮不可缓冲
     */
    public BufferDecision buffer(SignalingMessage msg, String ngapId, String rntiType) {
        long now = System.currentTimeMillis();

        if (!isEmpty(ngapId)) {
            bufferByNgap(msg, ngapId, now);
            return BufferDecision.bufferedByNgap(ngapId);
        }

        if (!isEmpty(rntiType)) {
            bufferByRnti(msg, rntiType, now);
            return BufferDecision.bufferedByRnti(rntiType);
        }

        return BufferDecision.notBufferable();
    }

    /**
     * 确保某个 ueId 进入对应的“等待索引补齐”队列。
     *
     * 当前语义保持不变：
     * - 如果该 ue 还没有 ngap 绑定，则进入 ngap 等待队列
     * - 如果该 ue 还没有 rnti 绑定，则进入 rnti 等待队列
     */
    public void ensureUeInWaitQueuesIfNeeded(
            String ueId,
            boolean ueNgapUnbound,
            boolean ueRntiUnbound
    ) {
        if (isEmpty(ueId)) {
            return;
        }

        if (ueNgapUnbound) {
            enqueueUeWaitNgapOnce(ueId);
        }

        if (ueRntiUnbound) {
            enqueueUeWaitRntiOnce(ueId);
        }
    }

    /**
     * 查看当前最早等待 ngap 的 ueId。
     *
     * 注意：
     * - 这里只 peek，不移除
     * - 真正出队由调用方决定
     */
    public String peekFirstWaitingUeForNgap() {
        return ueWaitNgap.peekFirst();
    }

    /**
     * 弹出当前最早等待 ngap 的 ueId。
     */
    public String pollFirstWaitingUeForNgap() {
        return pollUeWaitNgap();
    }

    /**
     * 查看当前最早等待 rnti 的 ueId。
     */
    public String peekFirstWaitingUeForRnti() {
        return ueWaitRntiType.peekFirst();
    }

    /**
     * 弹出当前最早等待 rnti 的 ueId。
     */
    public String pollFirstWaitingUeForRnti() {
        return pollUeWaitRnti();
    }

    /**
     * 在 ue 已经完成 ngap 绑定后，移除它的 ngap 等待标记。
     *
     * 注意：
     * - 当前实现只移除 queued 集合标记
     * - 队列中的历史残留项会在后续 peek/poll 时被跳过
     * - 这样做可以避免在线性队列中做代价较高的删除
     */
    public void removeUeWaitNgap(String ueId) {
        queuedUeWaitNgap.remove(ueId);
    }

    /**
     * 在 ue 已经完成 rnti 绑定后，移除它的 rnti 等待标记。
     */
    public void removeUeWaitRnti(String ueId) {
        queuedUeWaitRnti.remove(ueId);
    }

    /**
     * 弹出一个 ngap 候选索引。
     *
     * 注意：
     * - 这里只从候选队列取值
     * - 是否真的还未绑定，由上游 BindingResolver 再判断
     */
    public String pollFirstQueuedNgapCandidate() {
        return unboundNgapIds.pollFirst();
    }

    /**
     * 标记某个 ngap 候选已经出队，不再视为“已排队”。
     */
    public void markNgapDequeued(String ngapId) {
        queuedNgapIds.remove(ngapId);
    }

    /**
     * 弹出一个 rntiType 候选索引。
     */
    public String pollFirstQueuedRntiCandidate() {
        return unboundRntiTypes.pollFirst();
    }

    /**
     * 标记某个 rntiType 候选已经出队。
     */
    public void markRntiDequeued(String rntiType) {
        queuedRntiTypes.remove(rntiType);
    }

    /**
     * 根据 ngapId 释放对应的 pending 消息，并给这些消息补上 ueId。
     *
     * 当前语义保持不变：
     * - 释放后按入队时间排序
     * - 所有被释放的消息都写入同一个 ueId
     * - 释放完成后移除该 ngapId 的 queued 标记
     */
    public List<SignalingMessage> releaseNgapPending(String ngapId, String ueId) {
        return releasePendingByKey(
                ngapId,
                ueId,
                pendingByNgapId,
                queuedNgapIds
        );
    }

    /**
     * 根据 rntiType 释放对应的 pending 消息，并给这些消息补上 ueId。
     */
    public List<SignalingMessage> releaseRntiPending(String rntiType, String ueId) {
        return releasePendingByKey(
                rntiType,
                ueId,
                pendingByRntiType,
                queuedRntiTypes
        );
    }

    /**
     * 按 ngapId 缓冲消息，并将 ngapId 放入候选队列。
     */
    private void bufferByNgap(SignalingMessage msg, String ngapId, long now) {
        appendPendingMessage(pendingByNgapId, ngapId, msg, now);
        enqueueNgapCandidateOnce(ngapId);
    }

    /**
     * 按 rntiType 缓冲消息，并将 rntiType 放入候选队列。
     */
    private void bufferByRnti(SignalingMessage msg, String rntiType, long now) {
        appendPendingMessage(pendingByRntiType, rntiType, msg, now);
        enqueueRntiCandidateOnce(rntiType);
    }

    /**
     * 将消息追加到指定 pending map 中。
     *
     * 当前每个 key 对应一个按时间累积的列表。
     */
    private void appendPendingMessage(
            Map<String, List<PendingMsg>> pendingMap,
            String key,
            SignalingMessage msg,
            long now
    ) {
        pendingMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new PendingMsg(msg, now));
    }

    /**
     * 只在尚未入队时，把 ngapId 加入候选队列。
     */
    private void enqueueNgapCandidateOnce(String ngapId) {
        if (!isEmpty(ngapId) && queuedNgapIds.add(ngapId)) {
            unboundNgapIds.offerLast(ngapId);
        }
    }

    /**
     * 只在尚未入队时，把 rntiType 加入候选队列。
     */
    private void enqueueRntiCandidateOnce(String rntiType) {
        if (!isEmpty(rntiType) && queuedRntiTypes.add(rntiType)) {
            unboundRntiTypes.offerLast(rntiType);
        }
    }

    /**
     * 只在尚未入队时，把 ueId 加入“等待 ngap”队列。
     */
    private void enqueueUeWaitNgapOnce(String ueId) {
        if (queuedUeWaitNgap.add(ueId)) {
            ueWaitNgap.offerLast(ueId);
        }
    }

    /**
     * 只在尚未入队时，把 ueId 加入“等待 rnti”队列。
     */
    private void enqueueUeWaitRntiOnce(String ueId) {
        if (queuedUeWaitRnti.add(ueId)) {
            ueWaitRntiType.offerLast(ueId);
        }
    }

    /**
     * 弹出一个“等待 ngap”的 ueId，并同步清除它的排队标记。
     */
    private String pollUeWaitNgap() {
        String ueId = ueWaitNgap.pollFirst();
        if (ueId != null) {
            queuedUeWaitNgap.remove(ueId);
        }
        return ueId;
    }

    /**
     * 弹出一个“等待 rnti”的 ueId，并同步清除它的排队标记。
     */
    private String pollUeWaitRnti() {
        String ueId = ueWaitRntiType.pollFirst();
        if (ueId != null) {
            queuedUeWaitRnti.remove(ueId);
        }
        return ueId;
    }

    /**
     * 按 key 释放 pending 消息的通用模板。
     */
    private List<SignalingMessage> releasePendingByKey(
            String key,
            String ueId,
            Map<String, List<PendingMsg>> pendingMap,
            Set<String> queuedKeys
    ) {
        List<PendingMsg> list = pendingMap.remove(key);
        if (list == null || list.isEmpty()) {
            queuedKeys.remove(key);
            return List.of();
        }

        list.sort(Comparator.comparingLong(p -> p.ts));

        List<SignalingMessage> result = new ArrayList<>(list.size());
        for (PendingMsg pendingMsg : list) {
            pendingMsg.msg.setUeId(ueId);
            result.add(pendingMsg.msg);
        }

        queuedKeys.remove(key);
        return result;
    }

    /**
     * 清理某个 pending map 中的过期消息。
     *
     * 当前策略：
     * - 过期消息直接移除
     * - 如果某个 key 下所有消息都被清空，则整个 key 移除
     */
    private void cleanupPendingMap(Map<String, List<PendingMsg>> map, long expireBefore) {
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

    /**
     * 单条待绑定消息记录。
     *
     * 当前只记录：
     * - 原始消息对象
     * - 入队时间戳
     */
    private static class PendingMsg {
        final SignalingMessage msg;
        final long ts;

        PendingMsg(SignalingMessage msg, long ts) {
            this.msg = msg;
            this.ts = ts;
        }
    }

    /**
     * 记录本轮缓冲决策的结果。
     *
     * 用途：
     * - 告诉上游本轮是否已缓冲
     * - 告诉上游按哪种索引进行了缓冲
     * - 上游可据此决定是否尝试“索引反向绑定”
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
