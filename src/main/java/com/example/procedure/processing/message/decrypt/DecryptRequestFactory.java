package com.example.procedure.processing.message.decrypt;

import com.example.procedure.infrastructure.decrypt.gateway.DecryptRequest;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import org.springframework.stereotype.Component;

/**
 * Builds decrypt-gateway requests for message decrypt flows.
 *
 * This keeps protocol-specific request shaping and algorithm-name mapping out of the
 * main decrypt coordinator so the coordinator can focus on branching decisions.
 */
@Component
public class DecryptRequestFactory {

    /**
     * Builds one NAS decrypt request.
     */
    public DecryptRequest buildNasRequest(
            SignalingMessage message,
            UEContext context,
            NasInfo nas
    ) {
        return DecryptRequest.of(
                message.getMsgId(),
                message.getUeId(),
                message.getUeId(),
                "NAS",
                context.getKNasEnc(),
                context.getKNasInt(),
                mapNasEncAlgo(context.getNasCipherAlg()),
                mapNasIntAlgo(context.getNasIntAlg()),
                nas.getSeqNoInt(),
                1,
                message.getDirection(),
                nas.getCipherTextHex(),
                nas.getMsgAuthCodeHex(),
                0
        );
    }

    /**
     * Builds one AS/PDCP decrypt request.
     */
    public DecryptRequest buildAsRequest(
            SignalingMessage message,
            UEContext context,
            PdcpInfo pdcp
    ) {
        return DecryptRequest.of(
                message.getMsgId(),
                message.getUeId(),
                message.getUeId(),
                "AS",
                context.getKRrcEnc(),
                context.getKRrcInt(),
                mapRrcEncAlgo(context.getRrcCipherAlg()),
                mapRrcIntAlgo(context.getRrcIntAlg()),
                pdcp.getSeqNumInt(),
                0,
                message.getDirection(),
                pdcp.getSignallingDataHex(),
                pdcp.getMacHex(),
                0
        );
    }

    /**
     * Normalizes one encrypted-type marker so branching stays stable across callers.
     */
    public String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    private String mapNasEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    private String mapNasIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    private String mapRrcEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    private String mapRrcIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
