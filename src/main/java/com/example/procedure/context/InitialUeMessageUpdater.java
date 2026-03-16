package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NgapInfo;
import org.springframework.stereotype.Component;

/**
 * 处理 Initial UE Message：
 * - 提取 RAN_UE_NGAP_ID
 * - 建立 RAN_UE_NGAP_ID -> ueId 反查映射
 */
@Component
public class InitialUeMessageUpdater implements UeContextUpdater {

    @Override
    public boolean supports(SignalingMessage msg) {
        return "Initial UE Message".equals(msg.getMsgType());
    }

    @Override
    public void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        NgapInfo ngap = support.pickNgap(msg.getNgapInfoList());
        if (ngap == null) {
            return;
        }

        String ranUeNgapId = ngap.getRanUeNgapId();
        if (ranUeNgapId != null && !ranUeNgapId.isEmpty()) {
            ctx.setRanUeNgapId(ranUeNgapId);
            ctx.setAttachState("NGAP_INITIAL_UE_MESSAGE");
            support.saveRanMap(ranUeNgapId, ctx.getUeId());
        }
    }
}