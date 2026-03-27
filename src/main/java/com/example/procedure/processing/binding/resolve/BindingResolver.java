package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates the binding stage for one message.
 */
@Service
public class BindingResolver {
    // REFACTOR STEP: BINDING_SUBPACKAGE_REORG

    private final PendingBindingStore pendingBindingStore;
    private final BindingInputExtractor inputExtractor;
    private final UeIdResolutionPolicy ueIdResolutionPolicy;
    private final PendingBindingDecisionHandler pendingBindingDecisionHandler;
    private final UeWaitQueueRegistrar ueWaitQueueRegistrar;
    private final BindingExecutor bindingExecutor;
    private final BindingFlushCoordinator flushCoordinator;

    public BindingResolver(
            PendingBindingStore pendingBindingStore,
            BindingInputExtractor inputExtractor,
            UeIdResolutionPolicy ueIdResolutionPolicy,
            PendingBindingDecisionHandler pendingBindingDecisionHandler,
            UeWaitQueueRegistrar ueWaitQueueRegistrar,
            BindingExecutor bindingExecutor,
            BindingFlushCoordinator flushCoordinator
    ) {
        this.pendingBindingStore = pendingBindingStore;
        this.inputExtractor = inputExtractor;
        this.ueIdResolutionPolicy = ueIdResolutionPolicy;
        this.pendingBindingDecisionHandler = pendingBindingDecisionHandler;
        this.ueWaitQueueRegistrar = ueWaitQueueRegistrar;
        this.bindingExecutor = bindingExecutor;
        this.flushCoordinator = flushCoordinator;
    }

    /**
     * Runs the binding stage for one message, choosing between unresolved buffering and resolved execution.
     */
    public BindingResolutionResult resolve(SignalingMessage msg) {
        pendingBindingStore.cleanupExpiredPending();

        BindingInputs inputs = inputExtractor.extract(msg);
        String resolvedUeId = ueIdResolutionPolicy.resolve(inputs);

        if (isEmpty(resolvedUeId)) {
            return pendingBindingDecisionHandler.handle(msg, inputs);
        }

        return handleResolvedMessage(msg, inputs, resolvedUeId);
    }

    /**
     * Applies the resolved-ue path: stamp ueId, ensure wait queues, execute binding, then flush released messages.
     */
    private BindingResolutionResult handleResolvedMessage(
            SignalingMessage msg,
            BindingInputs inputs,
            String ueId
    ) {
        msg.setUeId(ueId);

        ueWaitQueueRegistrar.ensureRegistered(ueId);

        BindingExecution execution = bindingExecutor.execute(inputs, ueId);
        List<SignalingMessage> released = flushCoordinator.combineReleased(
                execution.releasedByNgap(),
                execution.releasedByRnti()
        );

        return BindingResolutionResult.ready(msg, released);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    static record BindingInputs(String ueId, String ngapId, String rntiType) {
    }

    static record BindingExecution(
            List<SignalingMessage> releasedByNgap,
            List<SignalingMessage> releasedByRnti
    ) {
    }
}
