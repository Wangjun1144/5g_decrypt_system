package com.example.procedure.processing.context.update;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;

/**
 * Message-specific updater for UE context changes.
 *
 * Each implementation owns the update logic for one class of signaling
 * message, replacing large conditional branches in the root context service.
 */
public interface UeContextUpdater {

    /**
     * Whether this updater supports the current signaling message.
     */
    boolean supports(SignalingMessage msg);

    /**
     * Apply one context update.
     *
     * @param msg current signaling message
     * @param ctx current UE context
     * @param procedureId current procedure instance id
     * @param support shared update helper functions
     */
    void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support);
}
