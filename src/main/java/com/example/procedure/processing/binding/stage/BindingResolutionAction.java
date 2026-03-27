package com.example.procedure.processing.binding.stage;

/**
 * Fine-grained actions produced by one binding decision.
 */
public enum BindingResolutionAction {
    BUFFER_CURRENT,
    FORWARD_CURRENT,
    RELEASE_PENDING
}
