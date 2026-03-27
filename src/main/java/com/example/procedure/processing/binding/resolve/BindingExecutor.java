package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Executes concrete binding writes and pending-message release.
 */
@Component
public class BindingExecutor {

    private final BindingStateStore bindingStateStore;
    private final PendingBindingStore pendingBindingStore;
    private final BindingFlushCoordinator flushCoordinator;

    public BindingExecutor(
            BindingStateStore bindingStateStore,
            PendingBindingStore pendingBindingStore,
            BindingFlushCoordinator flushCoordinator
    ) {
        this.bindingStateStore = bindingStateStore;
        this.pendingBindingStore = pendingBindingStore;
        this.flushCoordinator = flushCoordinator;
    }

    public BindingResolver.BindingExecution execute(
            BindingResolver.BindingInputs inputs,
            String ueId
    ) {
        boolean boundNgapNow = false;
        boolean boundRntiNow = false;

        List<SignalingMessage> releasedByNgap = List.of();
        List<SignalingMessage> releasedByRnti = List.of();

        if (canBindNgapNow(inputs.ngapId(), ueId)) {
            bindNgapToUe(inputs.ngapId(), ueId);
            releasedByNgap = flushCoordinator.flushByNgap(inputs.ngapId(), ueId);
            boundNgapNow = true;
        }

        if (canBindRntiNow(inputs.rntiType(), ueId)) {
            bindRntiToUe(inputs.rntiType(), ueId);
            releasedByRnti = flushCoordinator.flushByRnti(inputs.rntiType(), ueId);
            boundRntiNow = true;
        }

        if (!boundNgapNow && bindingStateStore.isUeNgapUnbound(ueId)) {
            String candidateNgap = pollFirstReallyUnboundNgap();
            if (candidateNgap != null) {
                bindNgapToUe(candidateNgap, ueId);
                releasedByNgap = flushCoordinator.flushByNgap(candidateNgap, ueId);
            }
        }

        if (!boundRntiNow && bindingStateStore.isUeRntiUnbound(ueId)) {
            String candidateRnti = pollFirstReallyUnboundRntiType();
            if (candidateRnti != null) {
                bindRntiToUe(candidateRnti, ueId);
                releasedByRnti = flushCoordinator.flushByRnti(candidateRnti, ueId);
            }
        }

        return new BindingResolver.BindingExecution(releasedByNgap, releasedByRnti);
    }

    private boolean canBindNgapNow(String ngapId, String ueId) {
        return !isEmpty(ngapId)
                && bindingStateStore.isNgapUnbound(ngapId)
                && bindingStateStore.isUeNgapUnbound(ueId);
    }

    private boolean canBindRntiNow(String rntiType, String ueId) {
        return !isEmpty(rntiType)
                && bindingStateStore.isRntiTypeUnbound(rntiType)
                && bindingStateStore.isUeRntiUnbound(ueId);
    }

    private void bindNgapToUe(String ngapId, String ueId) {
        bindingStateStore.bindNgapIdToUe(ngapId, ueId);
        pendingBindingStore.removeUeWaitNgap(ueId);
    }

    private void bindRntiToUe(String rntiType, String ueId) {
        bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
        pendingBindingStore.removeUeWaitRnti(ueId);
    }

    private String pollFirstReallyUnboundNgap() {
        while (true) {
            String ngapId = pendingBindingStore.pollFirstQueuedNgapCandidate();
            if (ngapId == null) {
                return null;
            }

            if (bindingStateStore.isNgapUnbound(ngapId)) {
                return ngapId;
            }

            pendingBindingStore.markNgapDequeued(ngapId);
        }
    }

    private String pollFirstReallyUnboundRntiType() {
        while (true) {
            String rntiType = pendingBindingStore.pollFirstQueuedRntiCandidate();
            if (rntiType == null) {
                return null;
            }

            if (bindingStateStore.isRntiTypeUnbound(rntiType)) {
                return rntiType;
            }

            pendingBindingStore.markRntiDequeued(rntiType);
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
