package com.example.procedure.context;

import com.example.procedure.keyderivation.KeyDerivationNative;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.RrcInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 RRC SecurityModeCommand：
 * - 提取 RRC 完整性/加密算法号
 * - 如果 KGNB(SecurityKeyHex) 已到位，则推导 KRrcEnc / KRrcInt
 * - 否则仅保存算法和状态，等待后续补偿推导
 */
@Component
public class RrcSecurityModeCommandUpdater implements UeContextUpdater {

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

        // 保持原行为：没有 KGNB 时先只保存状态，后续由补偿逻辑推导
        if (kgnb == null || kgnb.isEmpty()) {
            ctx.setAttachState("RRC_SMC");
            return;
        }

        int encNo = support.parseAlgNo123(cipherAlgStr);
        int intNo = support.parseAlgNo123(integrityAlgStr);

        int encAlgIdentity = support.mapAlgIdentity(encNo);
        int intAlgIdentity = support.mapAlgIdentity(intNo);

        String kRrcEnc = KeyDerivationNative.algorithmKeyDerivation(0x03, encAlgIdentity, kgnb);
        String kRrcInt = KeyDerivationNative.algorithmKeyDerivation(0x04, intAlgIdentity, kgnb);

        if (kRrcEnc != null && !kRrcEnc.isEmpty()) {
            ctx.setKRrcEnc(kRrcEnc);
        }
        if (kRrcInt != null && !kRrcInt.isEmpty()) {
            ctx.setKRrcInt(kRrcInt);
        }

        ctx.setAttachState("RRC_SMC");
    }
}