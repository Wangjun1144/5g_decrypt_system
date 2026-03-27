package com.example.procedure.processing.context.update;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.MacInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from an RRC Setup Complete message.
 *
 * It captures the current C-RNTI and advances the attach-state marker.
 */
@Component
public class RrcSetupCompleteUpdater implements UeContextUpdater {

    @Override
    public boolean supports(SignalingMessage msg) {
        return "RRCSetupComplete".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        MacInfo mac = msg.getMacInfo();
        if (mac == null) {
            return;
        }

        String crnti = mac.getRnti();
        if (crnti != null && !crnti.isEmpty()) {
            ctx.setCrnti(crnti);
            ctx.setAttachState("RRC_SETUP_COMPLETE");

            // Reserved for future cellId + C-RNTI reverse lookup support.
            // support.saveCrntiMap(cellId, crnti, ctx.getUeId());
        }
    }
}
