package com.example.procedure.processing.pending.store;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;

/**
 * pending 娑堟伅璁板綍銆?
 *
 * 璁捐鎰忓浘锛?
 * 1. 灏嗏€滅瓑寰呰В瀵嗘秷鎭€濈殑瀛樺偍瀵硅薄浠?service 涓娊绂?
 * 2. 涓哄悗缁垏鎹?Redis / MQ / Kafka waiting topic 鍋氱粺涓€妯″瀷鍑嗗
 * 3. 閬垮厤鍚勫鐩存帴渚濊禆 PendingMessageService.PendingItem 杩欑鍐呴儴绫?
 *
 * 褰撳墠闃舵绾︽潫锛?
 * - 浠嶇劧淇濇寔鍘熸湁瀛楁璇箟
 * - 涓嶆敼鍙樺師涓氬姟琛屼负
 */
public class PendingMessageRecord {

    /** 鍏ラ槦鏃堕棿鎴?*/
    private final long enqueueAt;

    /** 娑堟伅 ID锛屼究浜庢棩蹇椾笌鎺掓煡 */
    private final String msgId;

    /** 褰撳墠绛夊緟鍘熷洜锛屼緥濡?WAIT_NAS_KEYS / WAIT_RRC_KEYS / WAIT_ALG */
    private final DecryptAttemptResult.WaitReason reason;

    /** 鍘熷寰呭鐞嗘秷鎭?*/
    private final SignalingMessage message;

    public PendingMessageRecord(
            long enqueueAt,
            String msgId,
            DecryptAttemptResult.WaitReason reason,
            SignalingMessage message
    ) {
        this.enqueueAt = enqueueAt;
        this.msgId = msgId;
        this.reason = reason;
        this.message = message;
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
}
