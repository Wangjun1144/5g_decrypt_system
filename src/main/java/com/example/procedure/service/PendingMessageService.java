package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.PendingMessageRecord;
import com.example.procedure.processing.pending.PendingMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated 阶段 1/2 过渡门面。
 *
 * 设计说明：
 * - 文档要求将等待状态抽象为 PendingMessageRepository。
 * - 为减少一次性改动范围，当前保留旧类名作为兼容门面
 * - 真正的状态存储已经下沉到 PendingMessageRepository
 *
 * 后续建议：
 * - 新代码优先直接依赖 PendingMessageRepository 或 processing.pending 包中的新服务
 * - 旧代码短期内仍可继续依赖 PendingMessageService
 */
@Deprecated
@Service
public class PendingMessageService {

    private final PendingMessageRepository repository;

    public PendingMessageService(PendingMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * 兼容旧接口：将一条消息放入 pending 队列。
     */
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        repository.enqueue(
                ueId,
                new PendingItem(
                        System.currentTimeMillis(),
                        safeMsgId(msg),
                        reason,
                        msg
                ).toRecord()
        );
    }

    /**
     * 兼容旧接口：批量拉取待处理消息。
     */
    public List<PendingItem> pollBatch(String ueId, int max) {
        return repository.pollBatch(ueId, max)
                .stream()
                .map(PendingItem::fromRecord)
                .collect(Collectors.toList());
    }

    /**
     * 兼容旧接口：重新入队。
     */
    public void requeue(String ueId, PendingItem item) {
        repository.requeue(ueId, item.toRecord());
    }

    public int size(String ueId) {
        return repository.size(ueId);
    }

    private String safeMsgId(SignalingMessage msg) {
        try {
            return msg.getMsgId();
        } catch (Exception e) {
            return "UNKNOWN_MSG";
        }
    }

    /**
     * 旧版兼容对象。
     *
     * 说明：
     * - 之所以暂时保留，是为了不让你前面已经改过的 PendingRetryService 再被迫一次性重写
     * - 后续可以逐步替换为 PendingMessageRecord
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