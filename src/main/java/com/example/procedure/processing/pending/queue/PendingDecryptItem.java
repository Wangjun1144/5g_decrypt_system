package com.example.procedure.processing.pending.queue;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.store.PendingMessageRecord;

/**
 * 寰呰В瀵嗙瓑寰呴」銆?
 *
 * 褰撳墠瀹氫綅锛?
 * - 杩欐槸 pending decrypt 鐨勬寮忕瓑寰呯姸鎬佹ā鍨?
 * - 鐢ㄤ簬琛ㄨ揪鈥滄煇鏉℃秷鎭鍦ㄧ瓑寰呰В瀵嗘潯浠舵弧瓒斥€?
 *
 * 杩欐牱鍋氱殑鎰忎箟锛?
 * - 鏂颁富閾句笉鍐嶇洿鎺ヤ緷璧?PendingMessageRecord 杩欑鍋忓簳灞傝褰曞璞?
 * - 鍚庣画鍙嚜鐒舵紨杩涗负 Redis waiting state / MQ waiting topic / retry worker 杈撳叆妯″瀷
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
