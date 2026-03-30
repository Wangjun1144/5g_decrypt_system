package com.example.procedure.model;

import com.example.procedure.model.result.ResultMetadata;
import com.example.procedure.model.result.ResultStatus;
import lombok.Data;

/**
 * Final result DTO produced for one signaling message after the main pipeline
 * completes.
 */
@Data
public class MessageProcessingResult {

    /**
     * UE identifier.
     */
    private String ueId;

    /**
     * Normalized message type.
     */
    private String msgType;

    /**
     * Message category.
     */
    private MessageCategory category;

    /**
     * Matched procedure id.
     */
    private String procedureId;

    /**
     * Matched procedure type.
     */
    private String procedureType;

    /**
     * Create one message processing result.
     */
    public MessageProcessingResult(
            String ueId,
            String msgType,
            MessageCategory category,
            String procedureId,
            String procedureType
    ) {
        this.ueId = ueId;
        this.msgType = msgType;
        this.category = category;
        this.procedureId = procedureId;
        this.procedureType = procedureType;
    }

    /**
     * Convert this DTO into the shared result metadata contract.
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        String primaryId = procedureId != null ? procedureId : ueId;

        return new ResultMetadata(
                "MessageProcessingResult",
                ResultStatus.SUCCESS,
                primaryId,
                "msgType=" + msgType + ",category=" + category + ",procedureType=" + procedureType
        );
    }

    /**
     * Returns whether this result is attached to a matched procedure.
     */
    public boolean hasMatchedProcedure() {
        return procedureId != null && !procedureId.isBlank();
    }

    /**
     * Returns whether the message category belongs to procedure processing.
     */
    public boolean isProcedureMessage() {
        return category == MessageCategory.PROCEDURE_DRIVING
                || category == MessageCategory.PROCEDURE_AUX;
    }

    /**
     * Returns the primary identifier that should represent this result externally.
     */
    public String primaryReferenceId() {
        return hasMatchedProcedure() ? procedureId : ueId;
    }
}
