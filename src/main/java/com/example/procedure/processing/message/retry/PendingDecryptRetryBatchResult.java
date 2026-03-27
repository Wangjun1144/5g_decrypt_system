package com.example.procedure.processing.message.retry;

/**
 * Aggregated retry result for one pending-decrypt batch.
 */
public class PendingDecryptRetryBatchResult {

    private final String ueId;
    private final int batchSize;
    private final int okCount;
    private final int requeueCount;
    private final int failCount;
    private final int remainingQueueSize;

    public PendingDecryptRetryBatchResult(
            String ueId,
            int batchSize,
            int okCount,
            int requeueCount,
            int failCount,
            int remainingQueueSize
    ) {
        this.ueId = ueId;
        this.batchSize = batchSize;
        this.okCount = okCount;
        this.requeueCount = requeueCount;
        this.failCount = failCount;
        this.remainingQueueSize = remainingQueueSize;
    }

    public String getUeId() {
        return ueId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getOkCount() {
        return okCount;
    }

    public int getRequeueCount() {
        return requeueCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public int getRemainingQueueSize() {
        return remainingQueueSize;
    }

    /**
     * Whether the batch contract represents any loaded work at all.
     */
    public boolean hasWork() {
        return batchSize > 0;
    }

    /**
     * Whether at least one pending item completed successfully in this batch.
     */
    public boolean hasSuccessfulRetries() {
        return okCount > 0;
    }

    /**
     * Whether at least one pending item had to stay in the queue.
     */
    public boolean hasRequeuedItems() {
        return requeueCount > 0;
    }

    /**
     * Whether at least one pending item failed terminally in this batch.
     */
    public boolean hasFailures() {
        return failCount > 0;
    }
}
