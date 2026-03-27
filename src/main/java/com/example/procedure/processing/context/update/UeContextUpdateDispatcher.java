package com.example.procedure.processing.context.update;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatcher that selects and invokes the first matching UE-context updater.
 */
@Component
public class UeContextUpdateDispatcher {

    private final List<UeContextUpdater> updaters;

    public UeContextUpdateDispatcher(List<UeContextUpdater> updaters) {
        this.updaters = updaters;
    }

    public void dispatch(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        for (UeContextUpdater updater : updaters) {
            if (updater.supports(msg)) {
                updater.update(msg, ctx, procedureId, support);
                return;
            }
        }
    }
}
