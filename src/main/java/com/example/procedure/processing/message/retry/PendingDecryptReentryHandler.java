package com.example.procedure.processing.message.retry;

import com.example.procedure.processing.message.runtime.MessageProcessingRequest;

/**
 * Callback used by pending-retry logic to re-enter the main message pipeline.
 */
@FunctionalInterface
public interface PendingDecryptReentryHandler {

    void reenter(MessageProcessingRequest request);
}
