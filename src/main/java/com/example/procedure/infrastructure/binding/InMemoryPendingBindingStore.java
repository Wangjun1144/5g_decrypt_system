package com.example.procedure.infrastructure.binding;

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
 * 基于内存的待绑定缓冲状态存储实现。
 *
 * 当前定位：
 * 1. 这是 binding 阶段正式的 pending 状态基础设施实现
 * 2. 负责维护待绑定消息缓冲、候选索引队列、ue 等待队列
 * 3. 业务编排层不应直接关心这些底层容器结构
 */
@Component
public class InMemoryPendingBindingStore {

    /**
     * 当前待绑定消息的生存时间。
     */
    private static final Duration PENDING_TTL = Duration.ofSeconds(120);

    /**
     * 按 ngapId 缓冲的 pending 消息。
     */
    private final Map<String, List<PendingMsg>> pendingByNgapId = new ConcurrentHashMap<>();

    /**
     * 按 rntiType 缓冲的 pending 消息。
     */
    private final Map<String, List<PendingMsg>> pendingByRntiType = new ConcurrentHashMap<>();

    /**
     * 尚未绑定的 ngapId 候选队列。
     */
    private final Deque<String> unboundNgapIds = new ConcurrentLinkedDeque<>();

    /**
     * 尚未绑定的 rntiType 候选队列。
     */
    private final Deque<String> unboundRntiTypes = new ConcurrentLinkedDeque<>();

    /**
     * 防止同一 ngapId 重复排队。
     */
    private final Set<String> queuedNgapIds = ConcurrentHashMap.newKeySet();

    /**
     * 防止同一 rntiType 重复排队。
     */
    private final Set<String> queuedRntiTypes = ConcurrentHashMap.newKeySet();

    /**
     * ueId 已出现，但仍在等待 ngapId。
     */
    private final Deque<String> ueWaitNgap = new ConcurrentLinkedDeque<>();

    /**
     * ueId 已出现，但仍在等待 rntiType。
     */
    private final Deque<String> ueWaitRntiType = new ConcurrentLinkedDeque<>();

    /**
     * 防止同一 ueId 重复进入 ngap 等待队列。
     */
    private final Set<String> queuedUeWaitNgap = ConcurrentHashMap.newKeySet();

    /**
     * 防止同一 ueId 重复进入 rnti 等待队列。
     */
    private final Set<String> queuedUeWaitRnti = ConcurrentHashMap.newKeySet();

    /**
     * 清理过期 pending。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    public void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long expireBefore = now - PENDING_TTL.toMillis();

        cleanupPendingMap(pendingByNgapId, expireBefore);
        cleanupPendingMap(pendingByRntiType, expireBefore);
    }

    /**
     * 按当前规则缓冲一条待绑定消息。
     *
     * @param msg 当前消息
     * @param ngapId 当前 ngapId
     * @param rntiType 当前 rntiType
     * @return 缓冲决策结果
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
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
     * 按需把某个 ueId 放入等待索引的队列。
     *
     * @param ueId ueId
     * @param ueNgapUnbound 是否缺 ngap
     * @param ueRntiUnbound 是否缺 rnti
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
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
     * 查看最早等待 ngap 的 ueId。
     *
     * @return ueId
     */
    public String peekFirstWaitingUeForNgap() {
        return ueWaitNgap.peekFirst();
    }

    /**
     * 弹出最早等待 ngap 的 ueId。
     *
     * @return ueId
     */
    public String pollFirstWaitingUeForNgap() {
        return pollUeWaitNgap();
    }

    /**
     * 查看最早等待 rnti 的 ueId。
     *
     * @return ueId
     */
    public String peekFirstWaitingUeForRnti() {
        return ueWaitRntiType.peekFirst();
    }

    /**
     * 弹出最早等待 rnti 的 ueId。
     *
     * @return ueId
     */
    public String pollFirstWaitingUeForRnti() {
        return pollUeWaitRnti();
    }

    /**
     * 移除某个 ue 的 ngap 等待标记。
     *
     * @param ueId ueId
     */
    public void removeUeWaitNgap(String ueId) {
        queuedUeWaitNgap.remove(ueId);
    }

    /**
     * 移除某个 ue 的 rnti 等待标记。
     *
     * @param ueId ueId
     */
    public void removeUeWaitRnti(String ueId) {
        queuedUeWaitRnti.remove(ueId);
    }

    /**
     * 弹出一个 ngap 候选索引。
     *
     * @return ngapId
     */
    public String pollFirstQueuedNgapCandidate() {
        return unboundNgapIds.pollFirst();
    }

    /**
     * 标记某个 ngap 候选已经出队。
     *
     * @param ngapId ngapId
     */
    public void markNgapDequeued(String ngapId) {
        queuedNgapIds.remove(ngapId);
    }

    /**
     * 弹出一个 rnti 候选索引。
     *
     * @return rntiType
     */
    public String pollFirstQueuedRntiCandidate() {
        return unboundRntiTypes.pollFirst();
    }

    /**
     * 标记某个 rnti 候选已经出队。
     *
     * @param rntiType rntiType
     */
    public void markRntiDequeued(String rntiType) {
        queuedRntiTypes.remove(rntiType);
    }

    /**
     * 根据 ngapId 释放对应 pending 消息。
     *
     * @param ngapId ngapId
     * @param ueId ueId
     * @return 释放后的消息列表
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
     * 根据 rntiType 释放对应 pending 消息。
     *
     * @param rntiType rntiType
     * @param ueId ueId
     * @return 释放后的消息列表
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
     * 按 ngapId 缓冲消息。
     *
     * @param msg 当前消息
     * @param ngapId ngapId
     * @param now 当前时间
     */
    private void bufferByNgap(SignalingMessage msg, String ngapId, long now) {
        appendPendingMessage(pendingByNgapId, ngapId, msg, now);
        enqueueNgapCandidateOnce(ngapId);
    }

    /**
     * 按 rntiType 缓冲消息。
     *
     * @param msg 当前消息
     * @param rntiType rntiType
     * @param now 当前时间
     */
    private void bufferByRnti(SignalingMessage msg, String rntiType, long now) {
        appendPendingMessage(pendingByRntiType, rntiType, msg, now);
        enqueueRntiCandidateOnce(rntiType);
    }

    /**
     * 把消息追加到某个 pending map。
     *
     * @param pendingMap 目标 map
     * @param key 索引 key
     * @param msg 当前消息
     * @param now 当前时间
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
     * 只在尚未入队时把 ngapId 加入候选队列。
     *
     * @param ngapId ngapId
     */
    private void enqueueNgapCandidateOnce(String ngapId) {
        if (!isEmpty(ngapId) && queuedNgapIds.add(ngapId)) {
            unboundNgapIds.offerLast(ngapId);
        }
    }

    /**
     * 只在尚未入队时把 rntiType 加入候选队列。
     *
     * @param rntiType rntiType
     */
    private void enqueueRntiCandidateOnce(String rntiType) {
        if (!isEmpty(rntiType) && queuedRntiTypes.add(rntiType)) {
            unboundRntiTypes.offerLast(rntiType);
        }
    }

    /**
     * 只在尚未入队时把 ueId 加入等待 ngap 队列。
     *
     * @param ueId ueId
     */
    private void enqueueUeWaitNgapOnce(String ueId) {
        if (queuedUeWaitNgap.add(ueId)) {
            ueWaitNgap.offerLast(ueId);
        }
    }

    /**
     * 只在尚未入队时把 ueId 加入等待 rnti 队列。
     *
     * @param ueId ueId
     */
    private void enqueueUeWaitRntiOnce(String ueId) {
        if (queuedUeWaitRnti.add(ueId)) {
            ueWaitRntiType.offerLast(ueId);
        }
    }

    /**
     * 弹出一个等待 ngap 的 ueId。
     *
     * @return ueId
     */
    private String pollUeWaitNgap() {
        String ueId = ueWaitNgap.pollFirst();
        if (ueId != null) {
            queuedUeWaitNgap.remove(ueId);
        }
        return ueId;
    }

    /**
     * 弹出一个等待 rnti 的 ueId。
     *
     * @return ueId
     */
    private String pollUeWaitRnti() {
        String ueId = ueWaitRntiType.pollFirst();
        if (ueId != null) {
            queuedUeWaitRnti.remove(ueId);
        }
        return ueId;
    }

    /**
     * 按 key 释放 pending 消息。
     *
     * @param key 索引 key
     * @param ueId ueId
     * @param pendingMap pending map
     * @param queuedKeys 已排队标记集合
     * @return 释放后的消息列表
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
     * @param map 目标 map
     * @param expireBefore 过期时间阈值
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

    /**
     * 判断字符串是否为空。
     *
     * @param s 输入字符串
     * @return true 表示为空
     */
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 单条待绑定消息记录。
     */
    private static class PendingMsg {

        /**
         * 原始消息。
         */
        final SignalingMessage msg;

        /**
         * 入队时间戳。
         */
        final long ts;

        /**
         * 构造待绑定消息记录。
         *
         * @param msg 原始消息
         * @param ts 入队时间戳
         */
        PendingMsg(SignalingMessage msg, long ts) {
            this.msg = msg;
            this.ts = ts;
        }
    }

    /**
     * 本轮缓冲决策结果。
     */
    public static class BufferDecision {

        /**
         * 当前是否已缓冲。
         */
        private final boolean buffered;

        /**
         * 当前是否按 ngapId 缓冲。
         */
        private final String bufferedNgapId;

        /**
         * 当前是否按 rntiType 缓冲。
         */
        private final String bufferedRntiType;

        /**
         * 构造缓冲决策结果。
         *
         * @param buffered 是否已缓冲
         * @param bufferedNgapId ngapId
         * @param bufferedRntiType rntiType
         */
        private BufferDecision(boolean buffered, String bufferedNgapId, String bufferedRntiType) {
            this.buffered = buffered;
            this.bufferedNgapId = bufferedNgapId;
            this.bufferedRntiType = bufferedRntiType;
        }

        /**
         * 构造按 ngapId 缓冲的结果。
         *
         * @param ngapId ngapId
         * @return 缓冲决策结果
         */
        public static BufferDecision bufferedByNgap(String ngapId) {
            return new BufferDecision(true, ngapId, null);
        }

        /**
         * 构造按 rntiType 缓冲的结果。
         *
         * @param rntiType rntiType
         * @return 缓冲决策结果
         */
        public static BufferDecision bufferedByRnti(String rntiType) {
            return new BufferDecision(true, null, rntiType);
        }

        /**
         * 构造“不可缓冲”结果。
         *
         * @return 缓冲决策结果
         */
        public static BufferDecision notBufferable() {
            return new BufferDecision(false, null, null);
        }

        /**
         * 判断是否已缓冲。
         *
         * @return true 表示已缓冲
         */
        public boolean isBuffered() {
            return buffered;
        }

        /**
         * 获取缓冲 ngapId。
         *
         * @return ngapId
         */
        public String getBufferedNgapId() {
            return bufferedNgapId;
        }

        /**
         * 获取缓冲 rntiType。
         *
         * @return rntiType
         */
        public String getBufferedRntiType() {
            return bufferedRntiType;
        }
    }
}
