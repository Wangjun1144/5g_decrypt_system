package com.example.procedure.processing.context.update;

import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NasInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from a NAS Security Mode Command.
 *
 * It records NAS algorithm selections and derives NAS keys immediately when
 * KAMF is already available.
 */
@Component
public class NasSecurityModeCommandUpdater implements UeContextUpdater {

    private final KeyDerivationService keyDerivationService;

    public NasSecurityModeCommandUpdater(KeyDerivationService keyDerivationService) {
        this.keyDerivationService = keyDerivationService;
    }

    @Override
    public boolean supports(SignalingMessage msg) {
        return "NAS SecurityModeCommand".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        NasInfo smcNas = support.pickNasSecurityMode(msg.getNasList());
        if (smcNas == null) {
            return;
        }

        String nasIntAlgStr = smcNas.getNas_integrityProtAlgorithm();
        String nasEncAlgStr = smcNas.getNas_cipheringAlgorithm();

        if (nasIntAlgStr != null && !nasIntAlgStr.isEmpty()) {
            ctx.setNasIntAlg(nasIntAlgStr);
        }
        if (nasEncAlgStr != null && !nasEncAlgStr.isEmpty()) {
            ctx.setNasCipherAlg(nasEncAlgStr);
        }

        String kamf = ctx.getKAmf();
        if (kamf == null || kamf.isEmpty()) {
            ctx.setAttachState("NAS_SMC");
            return;
        }

        int encNo = support.parseAlgNo123(nasEncAlgStr);
        int intNo = support.parseAlgNo123(nasIntAlgStr);

        int encAlgIdentity = support.mapAlgIdentity(encNo);
        int intAlgIdentity = support.mapAlgIdentity(intNo);

        String kNasEnc = keyDerivationService.algorithmKeyDerivation(0x01, encAlgIdentity, kamf);
        String kNasInt = keyDerivationService.algorithmKeyDerivation(0x02, intAlgIdentity, kamf);

        if (kNasEnc != null && !kNasEnc.isEmpty()) {
            ctx.setKNasEnc(kNasEnc);
        }
        if (kNasInt != null && !kNasInt.isEmpty()) {
            ctx.setKNasInt(kNasInt);
        }

        ctx.setAttachState("NAS_SMC");
    }
}
