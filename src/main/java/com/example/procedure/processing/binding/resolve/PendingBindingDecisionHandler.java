package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;
import org.springframework.stereotype.Component;

/**
 * Handles the unresolved-ue branch of binding, including buffering and opportunistic release attempts.
 */
@Component
public class PendingBindingDecisionHandler {

    private final PendingBindingStore pendingBindingStore;
    private final PendingBindingReleaseService pendingBindingReleaseService;

    public PendingBindingDecisionHandler(
            PendingBindingStore pendingBindingStore,
            PendingBindingReleaseService pendingBindingReleaseService
    ) {
        this.pendingBindingStore = pendingBindingStore;
        this.pendingBindingReleaseService = pendingBindingReleaseService;
    }

    /**
     * Buffers the current message and, when possible, uses the incoming clue to release a waiting UE bind.
     */
    public BindingResolutionResult handle(
            SignalingMessage msg,
            BindingResolver.BindingInputs inputs
    ) {
        PendingBindingStore.BufferDecision bufferDecision =
                pendingBindingStore.buffer(msg, inputs.ngapId(), inputs.rntiType());

        // Buffering may also unlock a previously waiting UE if the new clue is globally unbound and reusable.
        if (bufferDecision.isBuffered()) {
            if (!isEmpty(bufferDecision.getBufferedNgapId())) {
                pendingBindingReleaseService.tryBindIncomingNgapToWaitingUe(
                        bufferDecision.getBufferedNgapId()
                );
            } else if (!isEmpty(bufferDecision.getBufferedRntiType())) {
                pendingBindingReleaseService.tryBindIncomingRntiToWaitingUe(
                        bufferDecision.getBufferedRntiType()
                );
            }
        }

        return BindingResolutionResult.buffered();
    }

    /**
     * Shared blank guard for optional buffered clue values.
     */
    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
