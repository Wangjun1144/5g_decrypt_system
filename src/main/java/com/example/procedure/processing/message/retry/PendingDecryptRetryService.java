package com.example.procedure.processing.message.retry;

import com.example.procedure.model.UEContext;
import com.example.procedure.processing.pending.queue.PendingDecryptItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin orchestrator for retrying queued decrypt work when UE keys become available.
 */
@Service
public class PendingDecryptRetryService {
    // REFACTOR STEP: MESSAGE_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_DECRYPT_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_RETRY_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_MAIN_COORDINATOR_RENAME

    private final PendingDecryptBatchLoader batchLoader;
    private final PendingDecryptBatchReporter batchReporter;
    private final PendingDecryptItemRetryExecutor itemRetryExecutor;

    /**
     * Keeps retry orchestration intentionally small: precondition check, batch load,
     * item execution loop, and batch-level reporting.
     */
    public PendingDecryptRetryService(
            PendingDecryptBatchLoader batchLoader,
            PendingDecryptBatchReporter batchReporter,
            PendingDecryptItemRetryExecutor itemRetryExecutor
    ) {
        this.batchLoader = batchLoader;
        this.batchReporter = batchReporter;
        this.itemRetryExecutor = itemRetryExecutor;
    }

    /**
     * Retries queued decrypt items for one UE when the current context has enough keys to attempt work.
     */
    public PendingDecryptRetryBatchResult retryPendingDecrypt(
            String ueId,
            UEContext context,
            PendingDecryptReentryHandler reentryHandler
    ) {
        // Blank ids cannot map to a queue partition, so we stop before touching queue state.
        if (isBlank(ueId)) {
            return null;
        }

        RetryCapability capability = evaluateRetryCapability(context);
        // If neither NAS nor RRC keys are usable, polling the queue would just churn work.
        if (!capability.canRetryAnything()) {
            return null;
        }

        List<PendingDecryptItem> items = batchLoader.loadBatch(ueId);
        // No items means no batch event and no summary log; this keeps observability signal clean.
        if (items.isEmpty()) {
            return null;
        }

        batchReporter.publishBatchStart(ueId, items.size());

        RetryStats stats = new RetryStats();

        // Item execution owns decrypt/requeue/reentry details; the service only aggregates counters.
        for (PendingDecryptItem item : items) {
            stats.merge(itemRetryExecutor.execute(context, capability, item, reentryHandler));
        }

        PendingDecryptRetryBatchResult batchResult =
                stats.toBatchResult(ueId, items.size(), batchLoader.remainingQueueSize(ueId));
        batchReporter.logBatchSummary(batchResult);
        return batchResult;
    }

    /**
     * Evaluates which decrypt families are retriable with the current key material.
     */
    RetryCapability evaluateRetryCapability(UEContext context) {
        boolean canTryNas = nasKeyReady(context);
        boolean canTryRrc = rrcKeyReady(context);
        return new RetryCapability(canTryNas, canTryRrc);
    }

    /**
     * Returns whether NAS keys are ready in the current UE context.
     */
    private boolean nasKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKNasEnc())
                && !isBlank(context.getKNasInt());
    }

    /**
     * Returns whether RRC keys are ready in the current UE context.
     */
    private boolean rrcKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKRrcEnc())
                && !isBlank(context.getKRrcInt());
    }

    /**
     * Centralizes blank checks because retry gating relies on string presence in several places.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Describes what kinds of decrypt can be retried right now.
     */
    static class RetryCapability {

        private final boolean canTryNas;
        private final boolean canTryRrc;

        RetryCapability(boolean canTryNas, boolean canTryRrc) {
            this.canTryNas = canTryNas;
            this.canTryRrc = canTryRrc;
        }

        boolean canRetryAnything() {
            return canTryNas || canTryRrc;
        }

        boolean canTryNas() {
            return canTryNas;
        }

        boolean canTryRrc() {
            return canTryRrc;
        }
    }

    /**
     * Mutable counter bag for one batch; aggregation stays here so the orchestrator remains loop-oriented.
     */
    static class RetryStats {

        private int ok;
        private int requeue;
        private int fail;

        /**
         * Adds one item outcome into the batch totals.
         */
        void merge(PendingDecryptItemRetryResult outcome) {
            if (outcome == null) {
                return;
            }
            ok += outcome.okCount();
            requeue += outcome.requeueCount();
            fail += outcome.failCount();
        }

        int okCount() {
            return ok;
        }

        int requeueCount() {
            return requeue;
        }

        int failCount() {
            return fail;
        }

        /**
         * Converts counters into the immutable batch result contract.
         */
        PendingDecryptRetryBatchResult toBatchResult(
                String ueId,
                int batchSize,
                int remainingQueueSize
        ) {
            return new PendingDecryptRetryBatchResult(
                    ueId,
                    batchSize,
                    ok,
                    requeue,
                    fail,
                    remainingQueueSize
            );
        }
    }
}
