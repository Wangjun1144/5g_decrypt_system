package com.example.procedure.processing.message;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.model.MessageCategory;

import java.util.UUID;

/**
 * 主处理链阶段事件。
 *
 * 当前用途：
 * - 为单体内的关键阶段提供统一观测与审计边界
 * - 不改变现有同步执行模式
 *
 * 后续演进：
 * - 可映射为内部事件流
 * - 可接审计、回放、Kafka、outbox
 */
public class MessageStageEvent {

    private final String eventId;
    private final String eventType;
    private final String stageName;
    private final String correlationId;
    private final String ueId;
    private final String procedureId;
    private final String procedureTypeCode;
    private final String messageId;
    private final String messageType;
    private final Long frameNo;
    private final Long messageTimestamp;
    private final MessageCategory category;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final boolean reentry;
    private final boolean encrypted;
    private final String encryptedType;

    public MessageStageEvent(
            String stageName,
            String correlationId,
            String ueId,
            String procedureId,
            String procedureTypeCode,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageCategory category,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            boolean encrypted,
            String encryptedType
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "MessageStageEvent";
        this.stageName = stageName;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.procedureId = procedureId;
        this.procedureTypeCode = procedureTypeCode;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.category = category;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.encrypted = encrypted;
        this.encryptedType = encryptedType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStageName() {
        return stageName;
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

    public String getProcedureTypeCode() {
        return procedureTypeCode;
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

    public MessageCategory getCategory() {
        return category;
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

    public boolean isEncrypted() {
        return encrypted;
    }

    public String getEncryptedType() {
        return encryptedType;
    }
}
