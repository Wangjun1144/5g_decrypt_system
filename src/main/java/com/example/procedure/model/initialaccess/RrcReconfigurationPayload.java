package com.example.procedure.model.initialaccess;

import com.example.procedure.model.message.MessagePayload;
import lombok.Data;

/**
 * Payload extracted from an RRC Reconfiguration message.
 */
@Data
public class RrcReconfigurationPayload implements MessagePayload {

    /**
     * Whether the message contains DRB-UP security configuration.
     */
    private boolean hasDrbSecurityConfig;

    /**
     * Whether DRB-UP encryption is activated.
     */
    private boolean drbUpEncActivated;

    /**
     * Whether DRB-UP integrity protection is activated.
     */
    private boolean drbUpIntActivated;

    @Override
    public String getMsgType() {
        return "RRCReconfiguration";
    }

    /**
     * Stage-6 start condition: at least one DRB-UP security function is active.
     */
    public boolean isStartMsg() {
        return hasDrbSecurityConfig
                && (drbUpEncActivated || drbUpIntActivated);
    }
}
