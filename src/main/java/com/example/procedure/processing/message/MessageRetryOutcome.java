package com.example.procedure.processing.message;

import com.example.procedure.processing.message.retry.PendingDecryptRetryBatchResult;

/**
 * Captures what happened when the main pipeline gave pending decrypt work a
 * chance to run after the current message refreshed UE context.
 */
public class MessageRetryOutcome {

    private final boolean refreshedContextAvailable;
    private final PendingDecryptRetryBatchResult batchResult;

    private MessageRetryOutcome(
            boolean refreshedContextAvailable,
            PendingDecryptRetryBatchResult batchResult
    ) {
        this.refreshedContextAvailable = refreshedContextAvailable;
        this.batchResult = batchResult;
    }

    /**
     * Creates one retry-trigger outcome from the refreshed context and batch execution result.
     */
    public static MessageRetryOutcome of(
            boolean refreshedContextAvailable,
            PendingDecryptRetryBatchResult batchResult
    ) {
        return new MessageRetryOutcome(refreshedContextAvailable, batchResult);
    }

    /**
     * Whether the retry stage managed to reload any UE context before checking pending work.
     */
    public boolean hasRefreshedContext() {
        return refreshedContextAvailable;
    }

    /**
     * Whether the retry service actually processed a pending batch this round.
     */
    public boolean hasBatchResult() {
        return batchResult != null;
    }

    /**
     * Whether the retry round produced any visible batch activity.
     */
    public boolean hasVisibleRetryWork() {
        return batchResult != null && batchResult.hasWork();
    }

    public PendingDecryptRetryBatchResult getBatchResult() {
        return batchResult;
    }
}
