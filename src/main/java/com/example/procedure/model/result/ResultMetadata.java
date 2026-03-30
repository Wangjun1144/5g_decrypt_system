package com.example.procedure.model.result;

/**
 * Shared lightweight result contract for logs, events, and high-level
 * summaries across processing subdomains.
 */
public class ResultMetadata {

    /**
     * Name of the concrete result type that produced this summary.
     */
    private final String resultType;

    /**
     * Normalized result status.
     */
    private final ResultStatus status;

    /**
     * Primary identifier associated with the result, for example a procedure
     * id, UE id, or message id.
     */
    private final String primaryId;

    /**
     * Human-readable summary message.
     */
    private final String message;

    /**
     * Create one shared result summary.
     *
     * @param resultType concrete result type name
     * @param status normalized result status
     * @param primaryId primary business identifier
     * @param message human-readable summary
     */
    public ResultMetadata(
            String resultType,
            ResultStatus status,
            String primaryId,
            String message
    ) {
        this.resultType = resultType;
        this.status = status;
        this.primaryId = primaryId;
        this.message = message;
    }

    public String getResultType() {
        return resultType;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public String getMessage() {
        return message;
    }
}
