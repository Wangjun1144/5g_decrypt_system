package com.example.procedure.processing.binding.resolve;

import org.springframework.stereotype.Component;

/**
 * Ensures a resolved UE is represented in the wait queues for any identity clue that is still missing.
 */
@Component
public class UeWaitQueueRegistrar {

    private final BindingStateStore bindingStateStore;
    private final PendingBindingStore pendingBindingStore;

    public UeWaitQueueRegistrar(
            BindingStateStore bindingStateStore,
            PendingBindingStore pendingBindingStore
    ) {
        this.bindingStateStore = bindingStateStore;
        this.pendingBindingStore = pendingBindingStore;
    }

    /**
     * Registers the UE into wait queues only for the clue families that remain unbound right now.
     */
    public void ensureRegistered(String ueId) {
        pendingBindingStore.ensureUeInWaitQueuesIfNeeded(
                ueId,
                bindingStateStore.isUeNgapUnbound(ueId),
                bindingStateStore.isUeRntiUnbound(ueId)
        );
    }
}
