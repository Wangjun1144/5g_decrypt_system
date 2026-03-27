package com.example.procedure.processing.message.retry;

import com.example.procedure.processing.pending.queue.PendingDecryptItem;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads one bounded batch of pending decrypt items for a UE.
 */
@Component
public class PendingDecryptBatchLoader {

    private static final int DEFAULT_BATCH_SIZE = 200;

    private final PendingDecryptQueue pendingDecryptQueue;

    public PendingDecryptBatchLoader(PendingDecryptQueue pendingDecryptQueue) {
        this.pendingDecryptQueue = pendingDecryptQueue;
    }

    public List<PendingDecryptItem> loadBatch(String ueId) {
        return pendingDecryptQueue.pollBatch(ueId, DEFAULT_BATCH_SIZE);
    }

    /**
     * Returns the remaining queue size after one batch pass.
     */
    public int remainingQueueSize(String ueId) {
        return pendingDecryptQueue.size(ueId);
    }
}
