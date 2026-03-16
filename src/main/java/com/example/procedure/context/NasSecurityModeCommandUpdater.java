package com.example.procedure.context;

import com.example.procedure.keyderivation.KeyDerivationNative;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NasInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 NAS SecurityModeCommand：
 * - 提取 NAS 加密/完整性算法号
 * - 如果 KAMF 已到位，则推导 KNasEnc / KNasInt
 * - 否则仅记录算法和状态，等待后续补偿推导
 */
@Component
public class NasSecurityModeCommandUpdater implements UeContextUpdater {

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

        // 保持原行为：如果没有 KAMF，先只保存状态，后续由补偿逻辑推导
        if (kamf == null || kamf.isEmpty()) {
            ctx.setAttachState("NAS_SMC");
            return;
        }

        int encNo = support.parseAlgNo123(nasEncAlgStr);
        int intNo = support.parseAlgNo123(nasIntAlgStr);

        int encAlgIdentity = support.mapAlgIdentity(encNo);
        int intAlgIdentity = support.mapAlgIdentity(intNo);

        String kNasEnc = KeyDerivationNative.algorithmKeyDerivation(0x01, encAlgIdentity, kamf);
        String kNasInt = KeyDerivationNative.algorithmKeyDerivation(0x02, intAlgIdentity, kamf);

        if (kNasEnc != null && !kNasEnc.isEmpty()) {
            ctx.setKNasEnc(kNasEnc);
        }
        if (kNasInt != null && !kNasInt.isEmpty()) {
            ctx.setKNasInt(kNasInt);
        }

        ctx.setAttachState("NAS_SMC");
    }
}
