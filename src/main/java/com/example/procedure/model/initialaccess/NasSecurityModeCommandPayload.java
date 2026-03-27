package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from a NAS Security Mode Command.
 */
@Data
public class NasSecurityModeCommandPayload implements MessagePayload {

    /**
     * NAS encryption algorithm, for example NEA0/1/2/3.
     */
    private String nasEncAlg;

    /**
     * NAS integrity algorithm, for example NIA0/1/2/3.
     */
    private String nasIntAlg;

    @Override
    public String getMsgType() {
        return "NAS SecurityModeCommand";
    }

    /**
     * Stage-3 start condition: both NAS algorithms must be present.
     */
    public boolean isStartMsg() {
        return nasEncAlg != null && !nasEncAlg.isEmpty()
                && nasIntAlg != null && !nasIntAlg.isEmpty();
    }
}
