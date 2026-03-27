package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from an RRC Security Mode Command.
 */
@Data
public class RrcSecurityModeCommandPayload implements MessagePayload {

    /**
     * RRC signaling-plane encryption algorithm.
     */
    private String rrcEncAlg;

    /**
     * RRC signaling-plane integrity algorithm.
     */
    private String rrcIntAlg;

    /**
     * Optional UP encryption algorithm.
     */
    private String upEncAlg;

    /**
     * Optional UP integrity algorithm.
     */
    private String upIntAlg;

    @Override
    public String getMsgType() {
        return "RRC SecurityModeCommand";
    }

    /**
     * Stage-5 start condition: the two RRC algorithms must be present.
     */
    public boolean isStartMsg() {
        return rrcEncAlg != null && !rrcEncAlg.isEmpty()
                && rrcIntAlg != null && !rrcIntAlg.isEmpty();
    }
}
