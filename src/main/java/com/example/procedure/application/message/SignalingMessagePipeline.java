package com.example.procedure.application.message;

import com.example.procedure.model.SignalingMessage;

import java.util.List;

/**
 * Stable application boundary for one signaling message entering the main
 * processing chain.
 *
 * Upstream callers should depend on this contract instead of invoking lower
 * processing stages directly. It keeps the ingress edge stable while internal
 * processing stages continue evolving.
 */
public interface SignalingMessagePipeline {

    /**
     * Process one application-level ingress request.
     *
     * @param request ingress request
     */
    void process(SignalingMessageIngressRequest request);

    /**
     * Process one application-level ingress request and return the typed
     * pipeline outcome.
     */
    default MessagePipelineOutcome processDetailed(SignalingMessageIngressRequest request) {
        process(request);
        return MessagePipelineOutcome.of(request, null, List.of(), 0);
    }

    /**
     * Legacy convenience overload kept for compatibility with older callers.
     *
     * @param msg signaling message to process
     */
    default void process(SignalingMessage msg) {
        process(SignalingMessageIngressRequest.of(msg));
    }
}
