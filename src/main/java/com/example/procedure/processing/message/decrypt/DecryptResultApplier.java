package com.example.procedure.processing.message.decrypt;

import com.example.procedure.infrastructure.decrypt.gateway.DecryptGatewayResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.NasInfo;
import org.springframework.stereotype.Component;

/**
 * Applies successful decrypt output back onto the mutable message model.
 */
@Component
public class DecryptResultApplier {

    /**
     * Applies NAS decrypt output and target metadata to the message.
     */
    public void applyNasResult(
            SignalingMessage message,
            DecryptGatewayResult response,
            int nasIndex,
            NasInfo nas
    ) {
        message.setDecryptPlainHex(response.getPlainData());
        message.setDecryptMacHex(normalizeHex(response.getPlainMac()));
        message.setDecryptTargetLayer("NAS");
        message.setDecryptTargetNasIndex(nasIndex);
        message.setDecryptTargetNodeId(nas == null ? null : nas.getNodeId());
    }

    /**
     * Applies AS/PDCP decrypt output and target metadata to the message.
     */
    public void applyPdcpResult(SignalingMessage message, DecryptGatewayResult response) {
        message.setDecryptPlainHex(response.getPlainData());
        message.setDecryptMacHex(normalizeHex(response.getPlainMac()));
        message.setDecryptTargetLayer("PDCP");
        if (message.getPdcpInfo() != null) {
            message.setDecryptTargetNodeId(message.getPdcpInfo().getNodeId());
        }
    }

    /**
     * Normalizes gateway hex output before it is stored on the message.
     */
    private String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) {
            v = v.substring(2);
        }
        v = v.replace(":", "").replace(" ", "");
        return v.toLowerCase();
    }
}
