package com.example.procedure.processing.message.retry;

import com.example.procedure.model.message.MessageSourceType;
import com.example.procedure.processing.pending.event.PendingDecryptEvent;
import com.example.procedure.processing.pending.event.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes batch-level retry events and writes final batch summary logs.
 */
@Component
public class PendingDecryptBatchReporter {

    private static final Logger log = LoggerFactory.getLogger(PendingDecryptBatchReporter.class);

    private final PendingDecryptQueue pendingDecryptQueue;
    private final PendingDecryptEventPublisher pendingDecryptEventPublisher;

    public PendingDecryptBatchReporter(
            PendingDecryptQueue pendingDecryptQueue,
            PendingDecryptEventPublisher pendingDecryptEventPublisher
    ) {
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.pendingDecryptEventPublisher = pendingDecryptEventPublisher;
    }

    /**
     * Emits the batch-start event only after the orchestrator confirms there is real retry work.
     */
    public void publishBatchStart(String ueId, int batchSize) {
        PendingDecryptEvent event = new PendingDecryptEvent(
                PendingDecryptRetryAction.BATCH_START,
                null,
                ueId,
                null,
                null,
                null,
                null,
                MessageSourceType.REENTRY,
                PendingDecryptRetryAction.BATCH_START,
                true,
                null,
                null,
                null,
                pendingDecryptQueue.size(ueId),
                batchSize
        );
        pendingDecryptEventPublisher.publish(event);
    }

    /**
     * Writes the final batch counters and current queue remainder for operational visibility.
     */
    public void logBatchSummary(PendingDecryptRetryBatchResult result) {
        log.info("Pending decrypt retry done: ueId={}, batch={}, ok={}, requeue={}, fail={}, remain={}",
                result.getUeId(),
                result.getBatchSize(),
                result.getOkCount(),
                result.getRequeueCount(),
                result.getFailCount(),
                result.getRemainingQueueSize());
    }
}
