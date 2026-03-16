package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NgapInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 Initial Context Setup Request：
 * - 提取 SecurityKeyHex（这里相当于 KGNB）
 * - 若此前 RRC 算法号已到位，则补偿推导 KRrcEnc / KRrcInt
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

        // 与原逻辑保持一致：如果 RRC 算法号此前已到位，则补偿推导 RRC keys
        support.deriveRrcKeysIfPossible(ctx);
    }
}