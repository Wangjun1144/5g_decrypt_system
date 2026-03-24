package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.PendingDecryptItem;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated 旧的 pending 消息兼容门面。
 *
 * 当前阶段保留这个类的原因：
 * 1. 旧代码和旧测试可能仍然依赖这个类
 * 2. 新主链已经迁移到 PendingDecryptQueue + PendingDecryptItem
 * 3. 这里的职责已经收缩为“兼容旧接口签名”
 *
 * 后续建议：
 * - 新代码只依赖 processing.pending 包下的新边界
 * - 本类最终可迁入 legacy 包或删除
 */
@Deprecated
@Service
public class PendingMessageService {

    /**
     * 新的 pending 队列正式边界。
     *
     * 旧门面只负责把旧接口调用转发到这里。
     */
    private final PendingDecryptQueue queue;

    /**
     * 构造旧门面。
     *
     * @param queue 新的待解密队列边界
     */
    public PendingMessageService(PendingDecryptQueue queue) {
        this.queue = queue;
    }

    /**
     * 兼容旧接口：将一条消息放入 pending 队列。
     *
     * 这里仍然保留旧签名，
     * 内部直接委托给新的 PendingDecryptQueue。
     *
     * @param ueId   UE 标识
     * @param msg    待进入等待状态的消息
     * @param reason 当前进入等待的原因
     */
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        queue.enqueue(ueId, msg, reason);
    }

    /**
     * 兼容旧接口：批量拉取待处理消息。
     *
     * 注意：
     * - 新队列现在返回的是 PendingDecryptItem
     * - 旧门面需要把它转换回旧的 PendingItem 兼容对象
     *
     * @param ueId UE 标识
     * @param max  最多拉取多少条
     * @return 旧接口风格的 pending 列表
     */
    public List<PendingItem> pollBatch(String ueId, int max) {
        return queue.pollBatch(ueId, max)
                .stream()
                .map(PendingItem::fromItem)
                .collect(Collectors.toList());
    }

    /**
     * 兼容旧接口：把一条旧风格 pending 记录重新入队。
     *
     * 这里需要把旧的 PendingItem 转成新的 PendingDecryptItem。
     *
     * @param ueId UE 标识
     * @param item 旧接口风格的 pending 项
     */
    public void requeue(String ueId, PendingItem item) {
        queue.requeue(item.toPendingDecryptItem(ueId));
    }

    /**
     * 兼容旧接口：查看某个 UE 当前待解密队列大小。
     *
     * @param ueId UE 标识
     * @return 当前等待中的消息数量
     */
    public int size(String ueId) {
        return queue.size(ueId);
    }

    /**
     * 旧版本兼容对象。
     *
     * 当前存在的意义：
     * 1. 让旧调用方继续拿到熟悉的数据结构
     * 2. 作为旧接口和新模型之间的转换层
     *
     * 后续建议：
     * - 新代码不要继续使用它
     * - 最终统一迁移到 PendingDecryptItem
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
         * @param msgId     消息 ID
         * @param reason    等待原因
         * @param msg       原始消息
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

        /**
         * 把旧风格 PendingItem 转成新的 PendingDecryptItem。
         *
         * @param ueId UE 标识
         * @return 新的等待状态对象
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
         * 把新的 PendingDecryptItem 转回旧的 PendingItem。
         *
         * 这是旧兼容层最关键的转换方法之一。
         *
         * @param item 新的等待状态对象
         * @return 旧接口风格对象
         */
        public static PendingItem fromItem(PendingDecryptItem item) {
            return new PendingItem(
                    item.getEnqueueAt(),
                    item.getMsgId(),
                    item.getReason(),
                    item.getMessage()
            );
        }
    }
}
