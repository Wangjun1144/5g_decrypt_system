package com.example.procedure.application.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.stage.BindingStageCommand;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;
import com.example.procedure.processing.binding.stage.MessageBindingProcessor;
import org.springframework.stereotype.Service;

/**
 * Formal application-layer entry for message binding.
 *
 * New code should prefer this service instead of depending on processor internals directly.
 */
@Service
public class BindingApplicationService {

    private final MessageBindingProcessor bindingProcessor;

    public BindingApplicationService(MessageBindingProcessor bindingProcessor) {
        this.bindingProcessor = bindingProcessor;
    }

    /**
     * Processes one formal binding request and returns the typed binding-stage result.
     */
    public BindingResolutionResult process(BindingProcessRequest request) {
        return processDetailed(request).getResult();
    }

    /**
     * Processes one formal binding request and returns the full binding application outcome.
     */
    public BindingApplicationOutcome processDetailed(BindingProcessRequest request) {
        return BindingApplicationOutcome.of(request, bindingProcessor.process(toStageCommand(request)));
    }

    /**
     * Convenience adapter for callers that only have a raw signaling message.
     */
    public BindingResolutionResult process(SignalingMessage msg) {
        return process(new BindingProcessRequest(msg, null, null, null, false));
    }

    /**
     * Maps the application-layer request into the internal processing command.
     */
    private BindingStageCommand toStageCommand(BindingProcessRequest request) {
        return new BindingStageCommand(
                request.getMessage(),
                request.getSourceType(),
                request.getSourceName(),
                request.getCorrelationId(),
                request.isReentry()
        );
    }
}
