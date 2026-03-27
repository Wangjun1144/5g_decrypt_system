package com.example.procedure.processing.message.result;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.support.logging.StageLogRefs;

/**
 * Typed summary view for one message-processing result.
 *
 * This keeps structured summary data separate from plain log-string formatting so
 * reporters and future adapters can reuse the same typed contract.
 */
public class MessageProcessingSummary {

    private final String ueId;
    private final String msgType;
    private final MessageCategory category;
    private final String procedureId;
    private final String procedureType;
    private final ResultMetadata metadata;

    /**
     * Creates one typed summary for a finished message-processing result.
     */
    public MessageProcessingSummary(
            String ueId,
            String msgType,
            MessageCategory category,
            String procedureId,
            String procedureType,
            ResultMetadata metadata
    ) {
        this.ueId = ueId;
        this.msgType = msgType;
        this.category = category;
        this.procedureId = procedureId;
        this.procedureType = procedureType;
        this.metadata = metadata;
    }

    public String getUeId() {
        return ueId;
    }

    public String getMsgType() {
        return msgType;
    }

    public MessageCategory getCategory() {
        return category;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public String getProcedureType() {
        return procedureType;
    }

    public ResultMetadata getMetadata() {
        return metadata;
    }

    /**
     * Formats the summary as a stable one-line log fragment.
     */
    public String toLogString() {
        return "resultType=" + metadata.getResultType()
                + ",status=" + metadata.getStatus()
                + ",primaryId=" + StageLogRefs.safe(metadata.getPrimaryId())
                + ",ueId=" + StageLogRefs.safe(ueId)
                + ",msgType=" + StageLogRefs.safe(msgType)
                + ",category=" + category
                + ",procedureId=" + StageLogRefs.safe(procedureId)
                + ",procedureType=" + StageLogRefs.safe(procedureType)
                + ",message=" + StageLogRefs.safe(metadata.getMessage());
    }
}
