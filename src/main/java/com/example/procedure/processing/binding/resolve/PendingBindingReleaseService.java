package com.example.procedure.processing.binding.resolve;

import org.springframework.stereotype.Component;

/**
 * Binds newly observed ngap/rnti clues to waiting UEs when possible.
 */
@Component
public class PendingBindingReleaseService {

    private final BindingStateStore bindingStateStore;
    private final PendingBindingStore pendingBindingStore;

    public PendingBindingReleaseService(
            BindingStateStore bindingStateStore,
            PendingBindingStore pendingBindingStore
    ) {
        this.bindingStateStore = bindingStateStore;
        this.pendingBindingStore = pendingBindingStore;
    }

    public void tryBindIncomingNgapToWaitingUe(String ngapId) {
        if (isEmpty(ngapId) || !bindingStateStore.isNgapUnbound(ngapId)) {
            return;
        }

        while (true) {
            String ueId = pendingBindingStore.peekFirstWaitingUeForNgap();
            if (ueId == null) {
                return;
            }

            if (!bindingStateStore.isUeNgapUnbound(ueId)) {
                pendingBindingStore.pollFirstWaitingUeForNgap();
                continue;
            }

            pendingBindingStore.pollFirstWaitingUeForNgap();
            bindingStateStore.bindNgapIdToUe(ngapId, ueId);
            return;
        }
    }

    public void tryBindIncomingRntiToWaitingUe(String rntiType) {
        if (isEmpty(rntiType) || !bindingStateStore.isRntiTypeUnbound(rntiType)) {
            return;
        }

        while (true) {
            String ueId = pendingBindingStore.peekFirstWaitingUeForRnti();
            if (ueId == null) {
                return;
            }

            if (!bindingStateStore.isUeRntiUnbound(ueId)) {
                pendingBindingStore.pollFirstWaitingUeForRnti();
                continue;
            }

            pendingBindingStore.pollFirstWaitingUeForRnti();
            bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
            return;
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
