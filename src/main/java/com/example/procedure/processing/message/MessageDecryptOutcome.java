package com.example.procedure.processing.message;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageProcessingResult;

/**
 * Formalizes how the decrypt stage influences the main message pipeline.
 *
 * The goal is to stop leaking control semantics through ad-hoc null checks:
 * the coordinator can now ask whether decrypt wants to continue the main chain
 * or return the current result immediately.
 */
public class MessageDecryptOutcome {

    /**
     * The next main-chain action decided by the decrypt stage.
     */
    public enum Resolution {
        CONTINUE_MAIN_CHAIN,
        RETURN_CURRENT_RESULT
    }

    private final Resolution resolution;
    private final MessageProcessingResult result;
    private final DecryptAttemptResult decryptResult;
    private final boolean reentered;
    private final boolean pendingEnqueued;

    private MessageDecryptOutcome(
            Resolution resolution,
            MessageProcessingResult result,
            DecryptAttemptResult decryptResult,
            boolean reentered,
            boolean pendingEnqueued
    ) {
        this.resolution = resolution;
        this.result = result;
        this.decryptResult = decryptResult;
        this.reentered = reentered;
        this.pendingEnqueued = pendingEnqueued;
    }

    /**
     * Creates an outcome that lets the current main-chain pass continue.
     */
    public static MessageDecryptOutcome continueMainChain(DecryptAttemptResult decryptResult) {
        return new MessageDecryptOutcome(
                Resolution.CONTINUE_MAIN_CHAIN,
                null,
                decryptResult,
                false,
                false
        );
    }

    /**
     * Creates an outcome that stops the current main-chain pass and returns a result.
     */
    public static MessageDecryptOutcome earlyReturn(
            MessageProcessingResult result,
            DecryptAttemptResult decryptResult,
            boolean reentered,
            boolean pendingEnqueued
    ) {
        return new MessageDecryptOutcome(
                Resolution.RETURN_CURRENT_RESULT,
                result,
                decryptResult,
                reentered,
                pendingEnqueued
        );
    }

    /**
     * Whether the coordinator should continue with procedure and retry stages.
     */
    public boolean shouldContinueMainChain() {
        return resolution == Resolution.CONTINUE_MAIN_CHAIN;
    }

    /**
     * Whether the coordinator should return the current result immediately.
     */
    public boolean shouldReturnCurrentResult() {
        return resolution == Resolution.RETURN_CURRENT_RESULT;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public MessageProcessingResult getResult() {
        return result;
    }

    public DecryptAttemptResult getDecryptResult() {
        return decryptResult;
    }

    public boolean isReentered() {
        return reentered;
    }

    public boolean isPendingEnqueued() {
        return pendingEnqueued;
    }
}
