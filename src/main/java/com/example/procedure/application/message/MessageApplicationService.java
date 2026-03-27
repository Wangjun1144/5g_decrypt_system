package com.example.procedure.application.message;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.MessageProcessingCoordinator;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import org.springframework.stereotype.Service;

/**
 * Formal application-layer entry for one message entering the main processing chain.
 *
 * This service keeps application callers away from the internal main-chain coordinator
 * and makes the request contract explicit at the application boundary.
 */
@Service
public class MessageApplicationService {

    private final MessageProcessingCoordinator messageProcessingCoordinator;

    /**
     * Creates the formal message application entry.
     *
     * @param messageProcessingCoordinator internal main-chain coordinator
     */
    public MessageApplicationService(MessageProcessingCoordinator messageProcessingCoordinator) {
        this.messageProcessingCoordinator = messageProcessingCoordinator;
    }

    /**
     * Processes one typed message-processing request.
     */
    public MessageProcessingResult process(MessageProcessingRequest request) {
        return processDetailed(request).getResult();
    }

    /**
     * Processes one typed message-processing request and returns the formal
     * application-layer outcome.
     */
    public MessageApplicationOutcome processDetailed(MessageProcessingRequest request) {
        return MessageApplicationOutcome.of(
                request,
                messageProcessingCoordinator.process(request)
        );
    }

    /**
     * Convenience adapter for callers that only have an application ingress request.
     */
    public MessageProcessingResult process(SignalingMessageIngressRequest request) {
        return process(MessageProcessingRequest.fromIngressRequest(request));
    }

    /**
     * Convenience adapter for callers that want a typed application outcome from ingress data.
     */
    public MessageApplicationOutcome processDetailed(SignalingMessageIngressRequest request) {
        return processDetailed(MessageProcessingRequest.fromIngressRequest(request));
    }

    /**
     * Convenience adapter for direct in-memory callers that only hold a raw signaling message.
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        return process(MessageProcessingRequest.of(msg));
    }

    /**
     * Convenience adapter for in-memory callers that also want the typed application outcome.
     */
    public MessageApplicationOutcome processDetailed(SignalingMessage msg) {
        return processDetailed(MessageProcessingRequest.of(msg));
    }
}
