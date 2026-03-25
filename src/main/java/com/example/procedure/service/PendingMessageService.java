package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.legacy.service.LegacyPendingMessageFacade;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated 旧的 pending 消息兼容门面。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖这个类
 * - 新主链已经迁移到 PendingDecryptQueue + PendingDecryptItem
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 后续建议：
 * - 新代码只依赖 processing.pending 包下的新边界
 * - 本类最终可迁入真正的 legacy 包或删除
 */
@Deprecated
@Service
public class PendingMessageService {

    /**
     * 旧 pending 消息兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final LegacyPendingMessageFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧 pending 消息兼容 facade
     */
    public PendingMessageService(LegacyPendingMessageFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：将一条消息放入 pending 队列。
     *
     * @param ueId UE 标识
     * @param msg 待进入等待状态的消息
     * @param reason 当前进入等待的原因
     */
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        delegate.enqueue(ueId, msg, reason);
    }

    /**
     * 兼容旧接口：批量拉取待处理消息。
     *
     * @param ueId UE 标识
     * @param max 最多拉取多少条
     * @return 旧接口风格的 pending 列表
     */
    public List<PendingItem> pollBatch(String ueId, int max) {
        return delegate.pollBatch(ueId, max)
                .stream()
                .map(item -> new PendingItem(
                        item.enqueueAt,
                        item.msgId,
                        item.reason,
                        item.msg
                ))
                .collect(Collectors.toList());
    }

    /**
     * 兼容旧接口：把一条旧风格 pending 记录重新入队。
     *
     * @param ueId UE 标识
     * @param item 旧接口风格的 pending 项
     */
    public void requeue(String ueId, PendingItem item) {
        delegate.requeue(
                ueId,
                new LegacyPendingMessageFacade.LegacyPendingItem(
                        item.enqueueAt,
                        item.msgId,
                        item.reason,
                        item.msg
                )
        );
    }

    /**
     * 兼容旧接口：查看某个 UE 当前待解密队列大小。
     *
     * @param ueId UE 标识
     * @return 当前等待中的消息数量
     */
    public int size(String ueId) {
        return delegate.size(ueId);
    }

    /**
     * 旧版本兼容对象。
     */
    public static class PendingItem {

        /**
         * 入队时间戳。
         */
        public final long enqueueAt;

        /**
         * 消息 ID。
         */
        public final String msgId;

        /**
         * 当前等待原因。
         */
        public final DecryptAttemptResult.WaitReason reason;

        /**
         * 原始消息对象。
         */
        public final SignalingMessage msg;

        /**
         * 构造旧接口兼容对象。
         *
         * @param enqueueAt 入队时间
         * @param msgId 消息 ID
         * @param reason 等待原因
         * @param msg 原始消息
         */
        public PendingItem(
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
    }
}
