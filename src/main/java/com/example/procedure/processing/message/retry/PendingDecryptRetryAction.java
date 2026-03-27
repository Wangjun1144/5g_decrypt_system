package com.example.procedure.processing.message.retry;

/**
 * Stable action names used by pending-decrypt retry events.
 */
public final class PendingDecryptRetryAction {

    public static final String BATCH_START = "pending-retry-batch";
    public static final String RETRY_OK = "pending-retry-ok";
    public static final String RETRY_WAITING = "pending-retry-waiting";
    public static final String RETRY_FAILED = "pending-retry-failed";
    public static final String RETRY_SKIP = "pending-retry-skip";
    public static final String RETRY_REQUEUE = "pending-retry-requeue";

    private PendingDecryptRetryAction() {
    }
}
