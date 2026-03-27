package com.example.procedure.processing.context.event;

import com.example.procedure.model.message.MessageSourceType;

import java.util.UUID;

/**
 * UEContext 鏇存柊浜嬩欢銆?
 *
 * 褰撳墠鐢ㄩ€旓細
 * 1. 涓轰笂涓嬫枃鏇存柊閾炬彁渚涙寮忎簨浠惰竟鐣?
 * 2. 璁╁崟浣撳唴鍙互缁熶竴瑙傛祴涓婁笅鏂囦綍鏃惰鍒涘缓銆佹洿鏂般€佽烦杩?
 * 3. 涓烘湭鏉ヤ笂涓嬫枃鏈嶅姟鐙珛銆佷簨浠跺洖鏀俱€乷utbox/Kafka 鎻愪緵绋冲畾浜嬩欢妯″瀷
 */
public class UeContextUpdatedEvent {

    /**
     * 浜嬩欢 ID銆?
     */
    private final String eventId;

    /**
     * 浜嬩欢绫诲瀷銆?
     */
    private final String eventType;

    /**
     * 褰撳墠鍔ㄤ綔銆?
     */
    private final String action;

    /**
     * 鍏宠仈 ID銆?
     */
    private final String correlationId;

    /**
     * UE ID銆?
     */
    private final String ueId;

    /**
     * 娴佺▼ ID銆?
     */
    private final String procedureId;

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
     * 鏄惁鍒涘缓浜嗘柊涓婁笅鏂囥€?
     */
    private final boolean created;

    /**
     * 鏄惁鎵ц浜嗘洿鏂般€?
     */
    private final boolean updated;

    /**
     * 缁撴灉璇存槑銆?
     */
    private final String message;

    /**
     * 鍙戝竷鏃堕棿鎴炽€?
     */
    private final long publishedAtMs;

    /**
     * 鏋勯€?UEContext 鏇存柊浜嬩欢銆?
     *
     * @param action 褰撳墠鍔ㄤ綔
     * @param correlationId 鍏宠仈 ID
     * @param ueId UE ID
     * @param procedureId 娴佺▼ ID
     * @param messageId 娑堟伅 ID
     * @param messageType 娑堟伅绫诲瀷
     * @param frameNo 甯у彿
     * @param messageTimestamp 娑堟伅鏃堕棿鎴?
     * @param sourceType 鏉ユ簮绫诲瀷
     * @param sourceName 鏉ユ簮鍚嶇О
     * @param reentry 鏄惁鍥炴祦
     * @param created 鏄惁鏂板缓
     * @param updated 鏄惁宸叉洿鏂?
     * @param message 缁撴灉璇存槑
     */
    public UeContextUpdatedEvent(
            String action,
            String correlationId,
            String ueId,
            String procedureId,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            boolean created,
            boolean updated,
            String message
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "UeContextUpdatedEvent";
        this.action = action;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.procedureId = procedureId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.created = created;
        this.updated = updated;
        this.message = message;
        this.publishedAtMs = System.currentTimeMillis();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAction() {
        return action;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getUeId() {
        return ueId;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public Long getFrameNo() {
        return frameNo;
    }

    public Long getMessageTimestamp() {
        return messageTimestamp;
    }

    public MessageSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isReentry() {
        return reentry;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean isUpdated() {
        return updated;
    }

    public String getMessage() {
        return message;
    }

    public long getPublishedAtMs() {
        return publishedAtMs;
    }
}
