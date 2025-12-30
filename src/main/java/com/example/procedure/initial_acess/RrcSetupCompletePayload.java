package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class RrcSetupCompletePayload implements MessagePayload {

    /** 是否携带 NAS Registration request */
    private boolean hasRegistrationRequest;

    /** 从 5GS mobile identity 解析出的 IMSI */
    private String imsi;

    /** 该 RRC 消息携带的 C-RNTI */
    private String crnti;

    @Override
    public String getMsgType() {
        return "RRCSetupComplete";
    }

    /** 是否满足“阶段0起始信令”的条件 */
    public boolean isStartMsg() {
        return hasRegistrationRequest
                && imsi != null && !imsi.isEmpty()
                && crnti != null && !crnti.isEmpty();
    }
}
