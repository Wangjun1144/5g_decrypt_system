package com.example.procedure.processing.context.update;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NgapInfo;
import org.springframework.stereotype.Component;

/**
 * Updates UE context from an Initial UE Message.
 *
 * It captures the RAN UE NGAP ID and writes the reverse lookup mapping used by
 * later message correlation.
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
