package com.example.procedure.processing.binding.stage;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.result.ResultMetadata;
import com.example.procedure.model.result.ResultStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed output of the binding stage.
 *
 * The result makes the stage contract explicit:
 * 1. The current message may be buffered and stop here.
 * 2. The current message may be ready to continue.
 * 3. Historical pending messages may be released together with the current message.
 */
public class BindingResolutionResult {

    /**
     * Whether the current message is buffered at the binding stage.
     */
    private final boolean buffered;

    /**
     * Messages that are ready to continue immediately after binding.
     */
    private final List<SignalingMessage> readyMessages;

    /**
     * Historical pending messages released by the current binding decision.
     */
    private final List<SignalingMessage> releasedMessages;

    /**
     * Creates one binding-stage result.
     */
    private BindingResolutionResult(
            boolean buffered,
            List<SignalingMessage> readyMessages,
            List<SignalingMessage> releasedMessages
    ) {
        this.buffered = buffered;
        this.readyMessages = readyMessages == null ? List.of() : Collections.unmodifiableList(readyMessages);
        this.releasedMessages = releasedMessages == null ? List.of() : Collections.unmodifiableList(releasedMessages);
    }

    /**
     * Creates a buffered result for messages that must wait for UE identity resolution.
     */
    public static BindingResolutionResult buffered() {
        return new BindingResolutionResult(true, List.of(), List.of());
    }

    /**
     * Creates a ready result with the current message and any released historical pending messages.
     */
    public static BindingResolutionResult ready(SignalingMessage current, List<SignalingMessage> released) {
        List<SignalingMessage> ready = new ArrayList<>();
        if (current != null) {
            ready.add(current);
        }
        return new BindingResolutionResult(false, ready, released);
    }

    /**
     * Returns whether the current message is buffered.
     */
    public boolean isBuffered() {
        return buffered;
    }

    /**
     * Returns whether downstream processing should continue for this result.
     */
    public boolean shouldContinueDownstream() {
        return !buffered;
    }

    /**
     * Returns the messages that are ready to continue immediately.
     */
    public List<SignalingMessage> getReadyMessages() {
        return readyMessages;
    }

    /**
     * Returns the historical pending messages released by this binding decision.
     */
    public List<SignalingMessage> getReleasedMessages() {
        return releasedMessages;
    }

    /**
     * Returns how many ready messages can continue immediately.
     */
    public int readyCount() {
        return readyMessages.size();
    }

    /**
     * Returns how many pending messages were released by this binding decision.
     */
    public int releasedCount() {
        return releasedMessages.size();
    }

    /**
     * Whether the current message itself is ready to continue downstream.
     */
    public boolean hasCurrentReadyMessage() {
        return !readyMessages.isEmpty();
    }

    /**
     * Whether this binding decision released any historical pending messages.
     */
    public boolean hasReleasedPendingMessages() {
        return !releasedMessages.isEmpty();
    }

    /**
     * Returns the fine-grained actions represented by this binding decision.
     */
    public List<BindingResolutionAction> actions() {
        List<BindingResolutionAction> actions = new ArrayList<>();
        if (buffered) {
            actions.add(BindingResolutionAction.BUFFER_CURRENT);
            return actions;
        }
        if (hasReleasedPendingMessages()) {
            actions.add(BindingResolutionAction.RELEASE_PENDING);
        }
        if (hasCurrentReadyMessage()) {
            actions.add(BindingResolutionAction.FORWARD_CURRENT);
        }
        return actions;
    }

    /**
     * Builds downstream emission order for the remaining callback-style application adapters.
     *
     * Released historical pending messages must be emitted before the current ready message.
     */
    public List<SignalingMessage> toDownstreamOrder() {
        List<SignalingMessage> result = new ArrayList<>(releasedMessages.size() + readyMessages.size());
        result.addAll(releasedMessages);
        result.addAll(readyMessages);
        return result;
    }

    /**
     * Converts the binding result to the shared result metadata contract.
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        String primaryId = null;
        if (!readyMessages.isEmpty() && readyMessages.get(0) != null) {
            primaryId = readyMessages.get(0).getMsgId();
        }

        String message = buffered
                ? "binding buffered"
                : "binding actions=" + actions() + ",ready=" + readyCount() + ",released=" + releasedCount();

        return new ResultMetadata(
                "BindingResolutionResult",
                buffered ? ResultStatus.BUFFERED : ResultStatus.SUCCESS,
                primaryId,
                message
        );
    }
}
