package com.example.procedure.processing.binding.resolve;

import org.springframework.stereotype.Component;

/**
 * Resolves the effective UE id from available binding clues.
 */
@Component
public class UeIdResolutionPolicy {

    private final BindingStateStore bindingStateStore;

    public UeIdResolutionPolicy(BindingStateStore bindingStateStore) {
        this.bindingStateStore = bindingStateStore;
    }

    public String resolve(BindingResolver.BindingInputs inputs) {
        if (!isEmpty(inputs.ueId())) {
            return inputs.ueId();
        }

        String ueIdByNgap = bindingStateStore.lookupUeIdByNgapId(inputs.ngapId());
        if (!isEmpty(ueIdByNgap)) {
            return ueIdByNgap;
        }

        return bindingStateStore.lookupUeIdByRntiType(inputs.rntiType());
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
