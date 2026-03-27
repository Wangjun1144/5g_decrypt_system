package com.example.procedure.processing.context.update;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NgapInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from an Initial Context Setup Request.
 *
 * It records the security key material carried by NGAP and compensates RRC key
 * derivation when RRC algorithm selections are already known.
 */
@Component
public class InitialContextSetupRequestUpdater implements UeContextUpdater {

    @Override
    public boolean supports(SignalingMessage msg) {
        return "Initial Context Setup Request".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        NgapInfo ngap = support.pickNgap(msg.getNgapInfoList());
        if (ngap == null) {
            return;
        }

        String securityKeyHex = ngap.getSecurityKeyHex();
        if (securityKeyHex != null && !securityKeyHex.isEmpty()) {
            ctx.setSecurityKeyHex(securityKeyHex);
            ctx.setAttachState("INITIAL_CONTEXT_SETUP");
        }

        support.deriveRrcKeysIfPossible(ctx);
    }
}
