package com.example.procedure.application.message;

import com.example.procedure.application.binding.BindingApplicationOutcome;

import java.util.List;

/**
 * Typed outcome for one application-edge signaling pipeline pass.
 */
public class MessagePipelineOutcome {

    private final SignalingMessageIngressRequest ingressRequest;
    private final BindingApplicationOutcome bindingOutcome;
    private final List<MessageApplicationOutcome> messageOutcomes;
    private final int processedMessageCount;

    private MessagePipelineOutcome(
            SignalingMessageIngressRequest ingressRequest,
            BindingApplicationOutcome bindingOutcome,
            List<MessageApplicationOutcome> messageOutcomes,
            int processedMessageCount
    ) {
        this.ingressRequest = ingressRequest;
        this.bindingOutcome = bindingOutcome;
        this.messageOutcomes = messageOutcomes == null ? List.of() : List.copyOf(messageOutcomes);
        this.processedMessageCount = processedMessageCount;
    }

    /**
     * Creates one immutable pipeline outcome.
     */
    public static MessagePipelineOutcome of(
            SignalingMessageIngressRequest ingressRequest,
            BindingApplicationOutcome bindingOutcome,
            List<MessageApplicationOutcome> messageOutcomes,
            int processedMessageCount
    ) {
        return new MessagePipelineOutcome(ingressRequest, bindingOutcome, messageOutcomes, processedMessageCount);
    }

    public SignalingMessageIngressRequest getIngressRequest() {
        return ingressRequest;
    }

    public int getProcessedMessageCount() {
        return processedMessageCount;
    }

    public BindingApplicationOutcome getBindingOutcome() {
        return bindingOutcome;
    }

    public List<MessageApplicationOutcome> getMessageOutcomes() {
        return messageOutcomes;
    }

    /**
     * Whether any message reached the main message application service after binding.
     */
    public boolean hasProcessedMessages() {
        return processedMessageCount > 0;
    }

    /**
     * Whether the binding stage buffered the current message at the application edge.
     */
    public boolean isBufferedAtBinding() {
        return bindingOutcome != null && bindingOutcome.isBuffered();
    }
}
