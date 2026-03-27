package com.example.procedure.application.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;

import java.util.List;

/**
 * Typed application-layer outcome for the binding stage.
 */
public class BindingApplicationOutcome {

    private final BindingProcessRequest request;
    private final BindingResolutionResult result;

    private BindingApplicationOutcome(
            BindingProcessRequest request,
            BindingResolutionResult result
    ) {
        this.request = request;
        this.result = result;
    }

    /**
     * Creates one immutable binding application outcome.
     */
    public static BindingApplicationOutcome of(
            BindingProcessRequest request,
            BindingResolutionResult result
    ) {
        return new BindingApplicationOutcome(request, result);
    }

    public BindingProcessRequest getRequest() {
        return request;
    }

    public BindingResolutionResult getResult() {
        return result;
    }

    /**
     * Whether the current message is buffered and should stop at binding.
     */
    public boolean isBuffered() {
        return result != null && result.isBuffered();
    }

    /**
     * Whether any message is ready to continue into the main message application service.
     */
    public boolean hasMessagesReadyForMainProcessing() {
        return result != null && result.shouldContinueDownstream();
    }

    /**
     * Returns the downstream emission order expected by the application edge.
     */
    public List<SignalingMessage> toDownstreamOrder() {
        return result == null ? List.of() : result.toDownstreamOrder();
    }

    /**
     * Exposes the fine-grained binding actions at the application boundary.
     */
    public List<com.example.procedure.processing.binding.stage.BindingResolutionAction> actions() {
        return result == null ? List.of() : result.actions();
    }
}
