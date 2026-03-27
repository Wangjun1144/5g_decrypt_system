package com.example.procedure.processing.context.update;

import com.example.procedure.infrastructure.security.keyderivation.KamfDerivationRequest;
import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationService;
import com.example.procedure.infrastructure.security.keyderivation.KeyDerivationResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NUARInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from a NAUSF authentication response.
 *
 * It records SUPI and KSEAF, derives KAMF, and then compensates NAS key
 * derivation when NAS algorithm choices are already known.
 */
@Component
public class NausfAuthenticateResponseUpdater implements UeContextUpdater {

    private final KeyDerivationService keyDerivationService;

    public NausfAuthenticateResponseUpdater(KeyDerivationService keyDerivationService) {
        this.keyDerivationService = keyDerivationService;
    }

    @Override
    public boolean supports(SignalingMessage msg) {
        return "Nausf_UEAuthentication_Authenticate Response".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        NUARInfo nuar = msg.getNuarInfo();
        if (nuar == null) {
            return;
        }

        String kseaf = nuar.getKseafHex();
        String imsi = nuar.getImsi();

        if (imsi != null && !imsi.isEmpty()) {
            ctx.setSupi(imsi);
        }

        if (kseaf != null && !kseaf.isEmpty()) {
            ctx.setKSeaf(kseaf);
            ctx.setAttachState("AUTH_COMPLETED");
        }

        if (imsi != null && !imsi.isEmpty() && kseaf != null && !kseaf.isEmpty()) {
            byte[] abba = new byte[]{0x00, 0x00};
            KeyDerivationResult result = keyDerivationService.deriveKamf(
                    KamfDerivationRequest.of(imsi, abba, kseaf)
            );
            if (result.hasDerivedKey()) {
                ctx.setKAmf(result.getDerivedKey());
            }
        }

        support.deriveNasKeysIfPossible(ctx);
    }
}
