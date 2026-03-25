package com.example.procedure.legacy.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.PendingDecryptItem;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 旧 pending 消息入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 PendingMessageService 的兼容职责
 * 2. 把旧门面类型和新的 PendingDecryptQueue / PendingDecryptItem 隔开
 * 3. 为后续清理 service 包做准备
 */
@Service
public class LegacyPendingMessageFacade {

    /**
     * 新的 pending 解密队列。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final PendingDecryptQueue queue;

    /**
     * 构造旧 pending 消息兼容 facade。
     *
     * @param queue 新的 pending 解密队列
     */
    public LegacyPendingMessageFacade(PendingDecryptQueue queue) {
        this.queue = queue;
    }

    /**
     * 兼容旧入口：把消息放入 pending 队列。
     *
     * @param ueId UE 标识
     * @param msg 当前消息
     * @param reason 等待原因
     */
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        queue.enqueue(ueId, msg, reason);
    }

    /**
     * 兼容旧入口：批量拉取 pending 消息。
     *
     * @param ueId UE 标识
     * @param max 最大数量
     * @return 旧风格 pending 列表
     */
    public List<LegacyPendingItem> pollBatch(String ueId, int max) {
        return queue.pollBatch(ueId, max)
                .stream()
                .map(LegacyPendingItem::fromItem)
                .collect(Collectors.toList());
    }

    /**
     * 兼容旧入口：重新入队。
     *
     * @param ueId UE 标识
     * @param item 旧风格 pending 项
     */
    public void requeue(String ueId, LegacyPendingItem item) {
        queue.requeue(item.toPendingDecryptItem(ueId));
    }

    /**
     * 兼容旧入口：查看当前数量。
     *
     * @param ueId UE 标识
     * @return 当前数量
     */
    public int size(String ueId) {
        return queue.size(ueId);
    }

    /**
     * 旧风格 pending 项兼容对象。
     */
    public static class LegacyPendingItem {

        /**
         * 入队时间。
         */
        public final long enqueueAt;

        /**
         * 消息 ID。
         */
        public final String msgId;

        /**
         * 等待原因。
         */
        public final DecryptAttemptResult.WaitReason reason;

        /**
         * 原始消息。
         */
        public final SignalingMessage msg;

        /**
         * 构造旧风格 pending 项。
         *
         * @param enqueueAt 入队时间
         * @param msgId 消息 ID
         * @param reason 等待原因
         * @param msg 原始消息
         */
        public LegacyPendingItem(
                long enqueueAt,
                String msgId,
                DecryptAttemptResult.WaitReason reason,
                SignalingMessage msg
        ) {
            this.enqueueAt = enqueueAt;
            this.msgId = msgId;
            this.reason = reason;
            this.msg = msg;
        }

        /**
         * 转换成新的 PendingDecryptItem。
         *
         * @param ueId UE 标识
         * @return 新的待解密等待项
         */
        public PendingDecryptItem toPendingDecryptItem(String ueId) {
            return new PendingDecryptItem(
                    ueId,
                    enqueueAt,
                    msgId,
                    reason,
                    msg
            );
        }

        /**
         * 从新的 PendingDecryptItem 转成旧风格对象。
         *
         * @param item 新的待解密等待项
         * @return 旧风格 pending 项
         */
        public static LegacyPendingItem fromItem(PendingDecryptItem item) {
            return new LegacyPendingItem(
                    item.getEnqueueAt(),
                    item.getMsgId(),
                    item.getReason(),
                    item.getMessage()
            );
        }
    }
}
