package com.example.procedure.processing.binding.stage;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.event.BindingEventPublisher;
import com.example.procedure.processing.binding.event.BindingResolvedEvent;
import com.example.procedure.processing.binding.resolve.BindingResolver;
import org.springframework.stereotype.Service;

/**
 * Default implementation of the formal binding-stage processor.
 *
 * Responsibilities:
 * 1. Delegate binding decisions to {@link BindingResolver}.
 * 2. Publish one binding-stage event for observability.
 * 3. Keep application-edge request mapping outside this class.
 */
@Service
public class DefaultMessageBindingProcessor implements MessageBindingProcessor {
    // REFACTOR STEP: BINDING_SUBPACKAGE_REORG

    /**
     * Core binding decision component.
     */
    private final BindingResolver bindingResolver;

    /**
     * Binding-stage event publishing boundary.
     */
    private final BindingEventPublisher eventPublisher;

    /**
     * Creates the default binding processor.
     *
     * @param bindingResolver core binding resolver
     * @param eventPublisher binding-stage event publisher
     */
    public DefaultMessageBindingProcessor(
            BindingResolver bindingResolver,
            BindingEventPublisher eventPublisher
    ) {
        this.bindingResolver = bindingResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes one formal binding request and returns the typed result.
     */
    @Override
    public BindingResolutionResult process(BindingStageCommand request) {
        SignalingMessage msg = request.getMessage();
        BindingResolutionResult result = bindingResolver.resolve(msg);

        publishBindingEvent(request, result);
        return result;
    }

    /**
     * Publishes one binding-stage event after the resolver finishes.
     */
    private void publishBindingEvent(
            BindingStageCommand request,
            BindingResolutionResult result
    ) {
        SignalingMessage msg = request.getMessage();

        BindingResolvedEvent event = new BindingResolvedEvent(
                request.getCorrelationId(),
                msg.getUeId(),
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                request.getSourceType(),
                request.getSourceName(),
                request.isReentry(),
                result.isBuffered(),
                result.readyCount(),
                result.releasedCount()
        );

        eventPublisher.publish(event);
    }
}
