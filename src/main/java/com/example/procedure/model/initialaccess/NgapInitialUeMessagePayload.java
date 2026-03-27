package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from an NGAP Initial UE Message.
 */
@Data
public class NgapInitialUeMessagePayload implements MessagePayload {

    /**
     * Whether the message carries a NAS Registration Request.
     */
    private boolean hasRegistrationRequest;

    /**
     * RAN UE NGAP ID used to identify the UE on the NGAP side.
     */
    private String ranUeNgapId;

    /**
     * NCGI extracted from the user location information.
     */
    private String ncgi;

    @Override
    public String getMsgType() {
        return "Initial UE Message";
    }

    /**
     * Stage-1 start condition for the NGAP branch.
     */
    public boolean isStartMsg() {
        return hasRegistrationRequest
                && ranUeNgapId != null && !ranUeNgapId.isEmpty()
                && ncgi != null && !ncgi.isEmpty();
    }
}
