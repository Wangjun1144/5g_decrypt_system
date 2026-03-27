package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NgapInfo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts normalized binding clues from a signaling message.
 */
@Component
public class BindingInputExtractor {

    public BindingResolver.BindingInputs extract(SignalingMessage msg) {
        String ueId = normalize(msg == null ? null : msg.getUeId());
        String ngapId = extractRanUeNgapId(msg);
        String rntiType = extractRntiType(msg);
        return new BindingResolver.BindingInputs(ueId, ngapId, rntiType);
    }

    private String extractRanUeNgapId(SignalingMessage msg) {
        if (msg == null) {
            return null;
        }

        List<NgapInfo> ngapList = msg.getNgapInfoList();
        if (ngapList == null || ngapList.isEmpty()) {
            return null;
        }

        NgapInfo ngap = ngapList.get(0);
        return ngap == null ? null : normalize(ngap.getRanUeNgapId());
    }

    private String extractRntiType(SignalingMessage msg) {
        if (msg == null) {
            return null;
        }

        MacInfo mac = msg.getMacInfo();
        if (mac == null) {
            return null;
        }

        return normalize(mac.getRntiType());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
