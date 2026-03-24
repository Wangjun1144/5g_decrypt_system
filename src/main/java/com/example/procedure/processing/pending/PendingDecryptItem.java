package com.example.procedure.processing.pending;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;

/**
 * 待解密等待项。
 *
 * 当前定位：
 * - 这是 pending decrypt 的正式等待状态模型
 * - 用于表达“某条消息正在等待解密条件满足”
 *
 * 这样做的意义：
 * - 新主链不再直接依赖 PendingMessageRecord 这种偏底层记录对象
 * - 后续可自然演进为 Redis waiting state / MQ waiting topic / retry worker 输入模型
 */
public class PendingDecryptItem {

    private final String ueId;
    private final long enqueueAt;
    private final String msgId;
    private final DecryptAttemptResult.WaitReason reason;
    private final SignalingMessage message;

    public PendingDecryptItem(
            String ueId,
            long enqueueAt,
            String msgId,
            DecryptAttemptResult.WaitReason reason,
            SignalingMessage message
    ) {
        this.ueId = ueId;
        this.enqueueAt = enqueueAt;
        this.msgId = msgId;
        this.reason = reason;
        this.message = message;
    }

    public static PendingDecryptItem of(
            String ueId,
            SignalingMessage message,
            DecryptAttemptResult.WaitReason reason
    ) {
        return new PendingDecryptItem(
                ueId,
                System.currentTimeMillis(),
                safeMsgId(message),
                reason,
                message
        );
    }

    public String getUeId() {
        return ueId;
    }

    public long getEnqueueAt() {
        return enqueueAt;
    }

    public String getMsgId() {
        return msgId;
    }

    public DecryptAttemptResult.WaitReason getReason() {
        return reason;
    }

    public SignalingMessage getMessage() {
        return message;
    }

    public PendingMessageRecord toRecord() {
        return new PendingMessageRecord(
                enqueueAt,
                msgId,
                reason,
                message
        );
    }

    public static PendingDecryptItem fromRecord(String ueId, PendingMessageRecord record) {
        return new PendingDecryptItem(
                ueId,
                record.getEnqueueAt(),
                record.getMsgId(),
                record.getReason(),
                record.getMessage()
        );
    }

    private static String safeMsgId(SignalingMessage message) {
        try {
            return message == null || message.getMsgId() == null || message.getMsgId().isBlank()
                    ? "UNKNOWN_MSG"
                    : message.getMsgId();
        } catch (Exception e) {
            return "UNKNOWN_MSG";
        }
    }
}
