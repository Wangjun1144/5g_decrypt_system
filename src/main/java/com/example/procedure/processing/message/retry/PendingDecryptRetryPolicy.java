package com.example.procedure.processing.message.retry;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

/**
 * Centralizes retry-specific policy decisions so the executor can stay focused
 * on orchestration instead of branching rules.
 */
@Component
public class PendingDecryptRetryPolicy {

    private static final int MAX_DECRYPT_DEPTH = 4;

    /**
     * Normalizes encrypted type names before retry routing and event reporting.
     */
    public String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    /**
     * Checks whether the current retry capability can legally attempt this encrypted type.
     */
    public boolean canAttemptForEncType(
            String encType,
            PendingDecryptRetryService.RetryCapability capability
    ) {
        if ("NAS".equals(encType)) {
            return capability.canTryNas();
        }
        if ("PDCP".equals(encType)) {
            return capability.canTryRrc();
        }
        if ("NAS+PDCP".equals(encType)) {
            return capability.canTryNas() || capability.canTryRrc();
        }
        return true;
    }

    /**
     * Guards retry recursion depth so malformed or looping messages cannot churn forever.
     */
    public boolean hasReachedMaxDepth(SignalingMessage message) {
        return safeDecryptDepth(message) >= MAX_DECRYPT_DEPTH;
    }

    /**
     * Reads decrypt depth defensively so malformed messages do not break retry control.
     */
    public int safeDecryptDepth(SignalingMessage message) {
        if (message == null || message.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(message.getDecryptDepth(), 0);
    }

    /**
     * Shared blank check for retry metadata and string-based guards.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
