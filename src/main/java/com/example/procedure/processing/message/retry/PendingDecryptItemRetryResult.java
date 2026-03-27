package com.example.procedure.processing.message.retry;

/**
 * Typed outcome for retrying one pending-decrypt item.
 */
public class PendingDecryptItemRetryResult {

    public enum Status {
        SUCCESS,
        REQUEUED,
        FAILED,
        NONE
    }

    private final Status status;

    private PendingDecryptItemRetryResult(Status status) {
        this.status = status;
    }

    public static PendingDecryptItemRetryResult success() {
        return new PendingDecryptItemRetryResult(Status.SUCCESS);
    }

    public static PendingDecryptItemRetryResult requeued() {
        return new PendingDecryptItemRetryResult(Status.REQUEUED);
    }

    public static PendingDecryptItemRetryResult failure() {
        return new PendingDecryptItemRetryResult(Status.FAILED);
    }

    public static PendingDecryptItemRetryResult none() {
        return new PendingDecryptItemRetryResult(Status.NONE);
    }

    public Status getStatus() {
        return status;
    }

    public int okCount() {
        return status == Status.SUCCESS ? 1 : 0;
    }

    public int requeueCount() {
        return status == Status.REQUEUED ? 1 : 0;
    }

    public int failCount() {
        return status == Status.FAILED ? 1 : 0;
    }
}
