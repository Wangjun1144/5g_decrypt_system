package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from an NGAP Initial Context Setup Request.
 */
@Data
public class NgapInitialContextSetupReqPayload implements MessagePayload {

    /**
     * Initial KgNB value, usually represented as hex.
     */
    private String kgNb;

    @Override
    public String getMsgType() {
        return "Initial Context Setup Request";
    }

    /**
     * Stage-4 start condition: KgNB must be present.
     */
    public boolean isStartMsg() {
        return kgNb != null && !kgNb.isEmpty();
    }
}
