package com.example.procedure.processing.binding.stage;

/**
 * Formal processor contract for one binding-stage request.
 *
 * The processor itself now only exposes the typed request/result model.
 * Application-edge request mapping is intentionally kept outside this
 * processing boundary.
 */
public interface MessageBindingProcessor {

    /**
     * Processes one binding-stage request and returns the typed binding result.
     *
     * @param request binding-stage command with ingress metadata
     * @return typed binding result for buffering or downstream continuation
     */
    BindingResolutionResult process(BindingStageCommand request);
}
