package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.PdcpInfo;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class DecryptResultReentryServiceImpl implements DecryptResultReentryService {

    private final PlaintextPacketParseBridgeService plaintextPacketParseBridgeService;

    public DecryptResultReentryServiceImpl(
            PlaintextPacketParseBridgeService plaintextPacketParseBridgeService
    ) {
        this.plaintextPacketParseBridgeService = plaintextPacketParseBridgeService;
    }

    @Override
    public void reenter(SignalingMessage encryptedMsg,
                        Consumer<SignalingMessage> reparsedConsumer) throws Exception {
        Objects.requireNonNull(encryptedMsg, "encryptedMsg must not be null");
        Objects.requireNonNull(reparsedConsumer, "reparsedConsumer must not be null");

        String plainHex = encryptedMsg.getDecryptPlainHex();
        if (plainHex == null || plainHex.isBlank()) {
            return;
        }

        PlaintextDecodeRequest req = new PlaintextDecodeRequest();
        req.setPlainHex(plainHex);
        req.setProtocolHint(resolveProtocolHint(encryptedMsg));
        req.setTraceId(buildTraceId(encryptedMsg));
        req.setSourceMsgId(encryptedMsg.getMsgId());
        req.setUeId(encryptedMsg.getUeId());

        // 新增：把来源节点 ID 带进去
        req.setSourceNodeId(encryptedMsg.getDecryptTargetNodeId());

        Set<String> wanted = Set.of(
                "nas-5gs_raw", "nas-5gs", "nr-rrc",
                "mac-nr", "mac-nr_raw", "ngap", "http2", "json.object"
        );
        Set<String> enabledRaw = Set.of("nas-5gs_raw", "mac-nr_raw");

        plaintextPacketParseBridgeService.streamBuildAndParse(
                req,
                wanted,
                enabledRaw,
                reparsedConsumer
        );
    }

    private String resolveProtocolHint(SignalingMessage msg) {
        String encType = msg.getEncryptedType();
        if ("NAS".equalsIgnoreCase(encType)) {
            return "NAS_5GS";
        }

        if ("PDCP".equalsIgnoreCase(encType) || "NAS+PDCP".equalsIgnoreCase(encType)) {
            PdcpInfo pdcp = msg.getPdcpInfo();
            if (pdcp == null) {
                return "NR_RRC_UL_DCCH";
            }

            String dir = upper(pdcp.getDirection());
            String bearer = upper(pdcp.getBearerName());

            if ("CCCH".equals(bearer)) {
                if ("DL".equals(dir)) return "NR_RRC_DL_CCCH";
                return "NR_RRC_UL_CCCH";
            }

            if ("DCCH".equals(bearer)) {
                if ("DL".equals(dir)) return "NR_RRC_DL_DCCH";
                return "NR_RRC_UL_DCCH";
            }

            if ("DL".equals(dir)) return "NR_RRC_DL_DCCH";
            return "NR_RRC_UL_DCCH";
        }

        return "NR_RRC_UL_DCCH";
    }

    private String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private String buildTraceId(SignalingMessage msg) {
        String id = msg.getMsgId() == null ? "unknown" : msg.getMsgId();
        return "reentry_" + id;
    }
}