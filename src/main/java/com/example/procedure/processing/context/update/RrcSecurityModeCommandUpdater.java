package com.example.procedure.processing.context.update;

import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.RrcInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from an RRC Security Mode Command.
 *
 * It records RRC algorithm selections and derives RRC keys immediately when
 * KgNB is already available.
 */
@Component
public class RrcSecurityModeCommandUpdater implements UeContextUpdater {

    private final KeyDerivationService keyDerivationService;

    public RrcSecurityModeCommandUpdater(KeyDerivationService keyDerivationService) {
        this.keyDerivationService = keyDerivationService;
    }

    @Override
    public boolean supports(SignalingMessage msg) {
        return "RRC SecurityModeCommand".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        RrcInfo rrc = msg.getRrcInfo();
        if (rrc == null) {
            return;
        }

        String integrityAlgStr = rrc.getIntegrityProtAlgorithm();
        String cipherAlgStr = rrc.getCipheringAlgorithm();

        if (integrityAlgStr != null && !integrityAlgStr.isEmpty()) {
            ctx.setRrcIntAlg(integrityAlgStr);
        }
        if (cipherAlgStr != null && !cipherAlgStr.isEmpty()) {
            ctx.setRrcCipherAlg(cipherAlgStr);
        }

        String kgnb = ctx.getSecurityKeyHex();
        if (kgnb == null || kgnb.isEmpty()) {
            ctx.setAttachState("RRC_SMC");
            return;
        }

        int encNo = support.parseAlgNo123(cipherAlgStr);
        int intNo = support.parseAlgNo123(integrityAlgStr);

        int encAlgIdentity = support.mapAlgIdentity(encNo);
        int intAlgIdentity = support.mapAlgIdentity(intNo);

        String kRrcEnc = keyDerivationService.algorithmKeyDerivation(0x03, encAlgIdentity, kgnb);
        String kRrcInt = keyDerivationService.algorithmKeyDerivation(0x04, intAlgIdentity, kgnb);

        if (kRrcEnc != null && !kRrcEnc.isEmpty()) {
            ctx.setKRrcEnc(kRrcEnc);
        }
        if (kRrcInt != null && !kRrcInt.isEmpty()) {
            ctx.setKRrcInt(kRrcInt);
        }

        ctx.setAttachState("RRC_SMC");
    }
}
