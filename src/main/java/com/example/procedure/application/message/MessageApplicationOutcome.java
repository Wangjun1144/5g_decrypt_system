package com.example.procedure.application.message;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;

/**
 * Typed application-layer outcome for one main-chain processing request.
 */
public class MessageApplicationOutcome {

    private final MessageProcessingRequest request;
    private final MessageProcessingResult result;

    private MessageApplicationOutcome(
            MessageProcessingRequest request,
            MessageProcessingResult result
    ) {
        this.request = request;
        this.result = result;
    }

    /**
     * Creates one immutable application outcome.
     */
    public static MessageApplicationOutcome of(
            MessageProcessingRequest request,
            MessageProcessingResult result
    ) {
        return new MessageApplicationOutcome(request, result);
    }

    public MessageProcessingRequest getRequest() {
        return request;
    }

    public MessageProcessingResult getResult() {
        return result;
    }

    /**
     * Exposes whether the current application call was a reentry pass.
     */
    public boolean isReentry() {
        return request != null && request.isReentry();
    }

    /**
     * Exposes the correlation id that ties this application call to the outer ingress.
     */
    public String getCorrelationId() {
        return request == null ? null : request.getCorrelationId();
    }

    /**
     * Returns whether the application call produced any outward processing result.
     */
    public boolean hasResult() {
        return result != null;
    }
}
