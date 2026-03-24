package com.example.procedure.processing.dispatch;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.model.MessageCategory;

import java.util.UUID;

/**
 * 流程分发事件。
 *
 * 当前用途：
 * - 作为单体内部的标准化事件对象
 * - 承接流程识别/分发阶段输出的关键元数据
 *
 * 后续演进：
 * - 可以直接映射到 MQ/Kafka 事件
 * - 可以作为审计事件、回放记录、归档记录的统一载体
 */
public class ProcedureDispatchedEvent {

    private final String eventId;
    private final String eventType;
    private final String correlationId;
    private final String ueId;
    private final String procedureId;
    private final String procedureTypeCode;
    private final MessageCategory category;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final boolean reentry;
    private final String messageId;
    private final String messageType;
    private final Long frameNo;
    private final Long messageTimestamp;
    private final String processingStage;

    public ProcedureDispatchedEvent(
            String correlationId,
            String ueId,
            String procedureId,
            String procedureTypeCode,
            MessageCategory category,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            String processingStage
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "ProcedureDispatchedEvent";
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.procedureId = procedureId;
        this.procedureTypeCode = procedureTypeCode;
        this.category = category;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.processingStage = processingStage;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
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

    public String getProcessingStage() {
        return processingStage;
    }
}
