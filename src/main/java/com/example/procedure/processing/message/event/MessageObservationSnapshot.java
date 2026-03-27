package com.example.procedure.processing.message.event;

import com.example.procedure.model.message.MessageSourceType;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;

/**
 * Shared observation snapshot for one message moving through the main chain.
 *
 * This snapshot keeps message identity, ingress metadata, procedure linkage, and
 * encryption-related fields in one place so events and structured logs can reuse
 * a single contract instead of rebuilding overlapping payloads repeatedly.
 */
public class MessageObservationSnapshot {

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

    /**
     * Creates one immutable observation snapshot.
     */
    public MessageObservationSnapshot(
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

    /**
     * Builds a snapshot from the shared runtime context.
     */
    public static MessageObservationSnapshot from(MessageProcessingContext context) {
        SignalingMessage message = context.getMessage();

        return new MessageObservationSnapshot(
                context.getCorrelationId(),
                message.getUeId(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode(),
                message.getMsgId(),
                message.getMsgType(),
                message.getFrameNo(),
                message.getTimestamp(),
                context.getCategory(),
                context.getSourceType(),
                context.getSourceName(),
                context.isReentry(),
                context.isEncrypted(),
                context.getEncryptedType()
        );
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
