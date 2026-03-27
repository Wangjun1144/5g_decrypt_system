package com.example.procedure.processing.pending.event;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.message.MessageSourceType;
import com.example.procedure.processing.message.event.MessageObservationSnapshot;

import java.util.UUID;

/**
 * pending decrypt 鐘舵€佷簨浠躲€?
 *
 * 褰撳墠鐢ㄩ€旓細
 * 1. 琛ㄨ揪寰呰В瀵嗘秷鎭湪绛夊緟/閲嶈瘯杩囩▼涓殑鐘舵€佸彉鍖?
 * 2. 涓哄崟浣撳唴鐨?pending decrypt 鐢熷懡鍛ㄦ湡鎻愪緵缁熶竴瑙傛祴鐐?
 * 3. 涓哄悗缁妸 waiting state / retry worker 鐙珛鍑哄幓棰勭暀浜嬩欢妯″瀷
 */
public class PendingDecryptEvent {

    /**
     * 浜嬩欢 ID銆?
     */
    private final String eventId;

    /**
     * 浜嬩欢绫诲瀷鍚嶇О銆?
     */
    private final String eventType;

    /**
     * 褰撳墠浜嬩欢鍔ㄤ綔銆?
     *
     * 鍏稿瀷鍊硷細
     * - pending-enqueued
     * - pending-retry-batch
     * - pending-retry-ok
     * - pending-retry-waiting
     * - pending-retry-failed
     * - pending-retry-skip
     * - pending-retry-requeue
     */
    private final String action;

    /**
     * 鍏宠仈 ID銆?
     */
    private final String correlationId;

    /**
     * UE 鏍囪瘑銆?
     */
    private final String ueId;

    /**
     * 娑堟伅 ID銆?
     */
    private final String messageId;

    /**
     * 娑堟伅绫诲瀷銆?
     */
    private final String messageType;

    /**
     * 甯у彿銆?
     */
    private final Long frameNo;

    /**
     * 娑堟伅鏃堕棿鎴炽€?
     */
    private final Long messageTimestamp;

    /**
     * 鏉ユ簮绫诲瀷銆?
     */
    private final MessageSourceType sourceType;

    /**
     * 鏉ユ簮鍚嶇О銆?
     */
    private final String sourceName;

    /**
     * 鏄惁鍥炴祦銆?
     */
    private final boolean reentry;

    /**
     * 褰撳墠绛夊緟鍘熷洜銆?
     */
    private final DecryptAttemptResult.WaitReason waitReason;

    /**
     * 褰撳墠鍔犲瘑绫诲瀷銆?
     */
    private final String encryptedType;

    /**
     * 褰撳墠閿欒淇℃伅銆?
     */
    private final String error;

    /**
     * 褰撳墠闃熷垪澶у皬銆?
     */
    private final Integer queueSize;

    /**
     * 褰撳墠閲嶈瘯鎵瑰ぇ灏忋€?
     */
    private final Integer batchSize;

    /**
     * 浜嬩欢鍙戝竷鏃堕棿鎴炽€?
     */
    private final long publishedAtMs;

    /**
     * 鏋勯€?pending decrypt 浜嬩欢銆?
     *
     * @param action 褰撳墠浜嬩欢鍔ㄤ綔
     * @param correlationId 鍏宠仈 ID
     * @param ueId UE 鏍囪瘑
     * @param messageId 娑堟伅 ID
     * @param messageType 娑堟伅绫诲瀷
     * @param frameNo 甯у彿
     * @param messageTimestamp 娑堟伅鏃堕棿鎴?
     * @param sourceType 鏉ユ簮绫诲瀷
     * @param sourceName 鏉ユ簮鍚嶇О
     * @param reentry 鏄惁鍥炴祦
     * @param waitReason 褰撳墠绛夊緟鍘熷洜
     * @param encryptedType 褰撳墠鍔犲瘑绫诲瀷
     * @param error 褰撳墠閿欒淇℃伅
     * @param queueSize 褰撳墠闃熷垪澶у皬
     * @param batchSize 褰撳墠鎵瑰ぇ灏?
     */
    public PendingDecryptEvent(
            String action,
            String correlationId,
            String ueId,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            DecryptAttemptResult.WaitReason waitReason,
            String encryptedType,
            String error,
            Integer queueSize,
            Integer batchSize
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "PendingDecryptEvent";
        this.action = action;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.waitReason = waitReason;
        this.encryptedType = encryptedType;
        this.error = error;
        this.queueSize = queueSize;
        this.batchSize = batchSize;
        this.publishedAtMs = System.currentTimeMillis();
    }

    /**
     * Creates a pending-decrypt event from the shared message observation snapshot.
     */
    public static PendingDecryptEvent fromObservation(
            String action,
            MessageObservationSnapshot snapshot,
            DecryptAttemptResult.WaitReason waitReason,
            String error,
            Integer queueSize,
            Integer batchSize
    ) {
        return new PendingDecryptEvent(
                action,
                snapshot.getCorrelationId(),
                snapshot.getUeId(),
                snapshot.getMessageId(),
                snapshot.getMessageType(),
                snapshot.getFrameNo(),
                snapshot.getMessageTimestamp(),
                snapshot.getSourceType(),
                snapshot.getSourceName(),
                snapshot.isReentry(),
                waitReason,
                snapshot.getEncryptedType(),
                error,
                queueSize,
                batchSize
        );
    }

    /**
     * 鑾峰彇浜嬩欢 ID銆?
     *
     * @return 浜嬩欢 ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 鑾峰彇浜嬩欢绫诲瀷銆?
     *
     * @return 浜嬩欢绫诲瀷
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 鑾峰彇鍔ㄤ綔鍚嶃€?
     *
     * @return 鍔ㄤ綔鍚?
     */
    public String getAction() {
        return action;
    }

    /**
     * 鑾峰彇鍏宠仈 ID銆?
     *
     * @return 鍏宠仈 ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 鑾峰彇 UE 鏍囪瘑銆?
     *
     * @return UE 鏍囪瘑
     */
    public String getUeId() {
        return ueId;
    }

    /**
     * 鑾峰彇娑堟伅 ID銆?
     *
     * @return 娑堟伅 ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 鑾峰彇娑堟伅绫诲瀷銆?
     *
     * @return 娑堟伅绫诲瀷
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 鑾峰彇甯у彿銆?
     *
     * @return 甯у彿
     */
    public Long getFrameNo() {
        return frameNo;
    }

    /**
     * 鑾峰彇娑堟伅鏃堕棿鎴炽€?
     *
     * @return 娑堟伅鏃堕棿鎴?
     */
    public Long getMessageTimestamp() {
        return messageTimestamp;
    }

    /**
     * 鑾峰彇鏉ユ簮绫诲瀷銆?
     *
     * @return 鏉ユ簮绫诲瀷
     */
    public MessageSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 鑾峰彇鏉ユ簮鍚嶇О銆?
     *
     * @return 鏉ユ簮鍚嶇О
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * 鍒ゆ柇鏄惁涓哄洖娴併€?
     *
     * @return true 琛ㄧず鍥炴祦
     */
    public boolean isReentry() {
        return reentry;
    }

    /**
     * 鑾峰彇绛夊緟鍘熷洜銆?
     *
     * @return 绛夊緟鍘熷洜
     */
    public DecryptAttemptResult.WaitReason getWaitReason() {
        return waitReason;
    }

    /**
     * 鑾峰彇鍔犲瘑绫诲瀷銆?
     *
     * @return 鍔犲瘑绫诲瀷
     */
    public String getEncryptedType() {
        return encryptedType;
    }

    /**
     * 鑾峰彇閿欒淇℃伅銆?
     *
     * @return 閿欒淇℃伅
     */
    public String getError() {
        return error;
    }

    /**
     * 鑾峰彇褰撳墠闃熷垪澶у皬銆?
     *
     * @return 褰撳墠闃熷垪澶у皬
     */
    public Integer getQueueSize() {
        return queueSize;
    }

    /**
     * 鑾峰彇褰撳墠鎵瑰ぇ灏忋€?
     *
     * @return 褰撳墠鎵瑰ぇ灏?
     */
    public Integer getBatchSize() {
        return batchSize;
    }

    /**
     * 鑾峰彇鍙戝竷鏃堕棿鎴炽€?
     *
     * @return 鍙戝竷鏃堕棿鎴?
     */
    public long getPublishedAtMs() {
        return publishedAtMs;
    }
}
