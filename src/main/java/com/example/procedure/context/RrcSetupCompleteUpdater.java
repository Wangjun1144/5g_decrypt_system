package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.MacInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 RRCSetupComplete：
 * - 提取 C-RNTI
 * - 推进 attachState
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

            // 预留：未来如果拿到 cellId，可在这里建立 cellId + crnti -> ueId 映射
            // support.saveCrntiMap(cellId, crnti, ctx.getUeId());
        }
    }
}