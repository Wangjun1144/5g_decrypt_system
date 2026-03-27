package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from a Nausf UE authentication response.
 */
@Data
public class NausfUeAuthRespPayload implements MessagePayload {

    /**
     * SUPI used to identify the UE.
     */
    private String supi;

    /**
     * KSEAF value returned by authentication.
     */
    private String kseaf;

    @Override
    public String getMsgType() {
        return "Nausf_UEAuthentication_Authenticate Response";
    }

    /**
     * Stage-2 start condition: both SUPI and KSEAF must be present.
     */
    public boolean isStartMsg() {
        return supi != null && !supi.isEmpty()
                && kseaf != null && !kseaf.isEmpty();
    }
}
