package com.example.procedure.processing.message.retry;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

/**
 * Builds stable retry identity values used by both events and reentry requests.
 */
@Component
public class PendingDecryptRetryIdentityFactory {

    /**
     * Derives a stable reentry source name for observability and downstream debugging.
     */
    public String buildRetrySourceName(SignalingMessage message) {
        return "pending-retry:" + safeMessageId(message);
    }

    /**
     * Derives a stable correlation id so the reentered message can be traced across stages.
     */
    public String buildRetryCorrelationId(SignalingMessage message) {
        return "pending-retry-" + safeMessageId(message);
    }

    /**
     * Falls back to a deterministic placeholder when the retry item has no message id.
     */
    private String safeMessageId(SignalingMessage message) {
        if (message == null || message.getMsgId() == null || message.getMsgId().isBlank()) {
            return "unknown";
        }
        return message.getMsgId();
    }
}
