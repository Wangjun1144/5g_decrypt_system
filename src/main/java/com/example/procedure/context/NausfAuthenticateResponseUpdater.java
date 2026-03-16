package com.example.procedure.context;

import com.example.procedure.keyderivation.KeyDerivationNative;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NUARInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 Nausf_UEAuthentication_Authenticate Response：
 * - 提取 SUPI/IMSI
 * - 提取 KSEAF
 * - 推导 KAMF
 * - 若 NAS 算法号已存在，则补偿推导 KNasEnc / KNasInt
 */
@Component
public class NausfAuthenticateResponseUpdater implements UeContextUpdater {

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
            String kamf = KeyDerivationNative.kamfFromKseaf(imsi, abba, kseaf);
            if (kamf != null && !kamf.isEmpty()) {
                ctx.setKAmf(kamf);
            }
        }

        // 与原逻辑保持一致：如果 NAS 算法号此前已到位，则在此补偿推导 NAS keys
        support.deriveNasKeysIfPossible(ctx);
    }
}
