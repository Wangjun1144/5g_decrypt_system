package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from an RRC Setup Complete message.
 */
@Data
public class RrcSetupCompletePayload implements MessagePayload {

    /**
     * Whether the message carries a NAS Registration Request.
     */
    private boolean hasRegistrationRequest;

    /**
     * IMSI extracted from the 5GS mobile identity.
     */
    private String imsi;

    /**
     * C-RNTI carried by the RRC message.
     */
    private String crnti;

    @Override
    public String getMsgType() {
        return "RRCSetupComplete";
    }

    /**
     * Stage-1 start condition for the RRC branch.
     */
    public boolean isStartMsg() {
        return hasRegistrationRequest
                && imsi != null && !imsi.isEmpty()
                && crnti != null && !crnti.isEmpty();
    }
}
