package com.example.procedure.initial_acess;

import com.example.procedure.model.MessagePayload;
import lombok.Data;

@Data
public class NgapInitialUeMessagePayload implements MessagePayload {

    /** 是否携带 NAS Registration request */
    private boolean hasRegistrationRequest;

    /** RAN UE NGAP ID，用于 NGAP 侧标识 UE */
    private String ranUeNgapId;

    /** NCGI（小区标识），来自 User Location Information */
    private String ncgi;

    @Override
    public String getMsgType() {
        return "Initial UE Message";
    }

    /** 是否满足“阶段1起始信令”的条件 */
    public boolean isStartMsg() {
        return hasRegistrationRequest
                && ranUeNgapId != null && !ranUeNgapId.isEmpty()
                && ncgi != null && !ncgi.isEmpty();
    }
}
