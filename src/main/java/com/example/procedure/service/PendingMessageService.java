package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import com.example.procedure.processing.pending.PendingMessageRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 旧的 pending 消息兼容门面。
 *
 * 当前阶段保留这个类的原因：
 * 1. 旧代码和旧测试可能仍依赖它
 * 2. 为了避免一次性大面积修改，保留兼容入口
 * 3. 真正的新边界已经转移到 PendingDecryptQueue
 *
 * 新代码使用建议：
 * - 不要继续优先依赖这个类
 * - 优先依赖 processing.pending 包下的新接口 PendingDecryptQueue
 *
 * 这一步之后，这个类的角色会更加明确：
 * - 它只是旧接口适配层
 * - 不再是推荐使用的 pending 队列入口
 */
@Deprecated
@Service
public class PendingMessageService {

    private final PendingDecryptQueue queue;

    public PendingMessageService(PendingDecryptQueue queue) {
        this.queue = queue;
    }

    /**
     * 兼容旧接口：将一条消息放入 pending 队列。
     */
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        queue.enqueue(ueId, msg, reason);
    }

    /**
     * 兼容旧接口：批量拉取待处理消息。
     */
    public List<PendingItem> pollBatch(String ueId, int max) {
        return queue.pollBatch(ueId, max)
                .stream()
                .map(PendingItem::fromRecord)
                .collect(Collectors.toList());
    }

    /**
     * 兼容旧接口：重新入队。
     */
    public void requeue(String ueId, PendingItem item) {
        queue.requeue(ueId, item.toRecord());
    }

    public int size(String ueId) {
        return queue.size(ueId);
    }

    /**
     * 旧版兼容对象。
     *
     * 当前仍保留它，是为了不让已接入旧接口的代码一次性全部改掉。
     * 后续可以逐步统一迁移到 PendingMessageRecord。
     */
    public static class PendingItem {
        public final long enqueueAt;
        public final String msgId;
        public final DecryptAttemptResult.WaitReason reason;
        public final SignalingMessage msg;

        public PendingItem(long enqueueAt, String msgId, DecryptAttemptResult.WaitReason reason, SignalingMessage msg) {
            this.enqueueAt = enqueueAt;
            this.msgId = msgId;
            this.reason = reason;
            this.msg = msg;
        }

        public PendingMessageRecord toRecord() {
            return new PendingMessageRecord(enqueueAt, msgId, reason, msg);
        }

        public static PendingItem fromRecord(PendingMessageRecord record) {
            return new PendingItem(
                    record.getEnqueueAt(),
                    record.getMsgId(),
                    record.getReason(),
                    record.getMessage()
            );
        }
    }
}
