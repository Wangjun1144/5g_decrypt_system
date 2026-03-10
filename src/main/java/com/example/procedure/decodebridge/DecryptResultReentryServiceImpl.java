package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;
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
            return "NR_RRC_UL_DCCH";
        }
        return "NR_RRC_UL_DCCH";
    }

    private String buildTraceId(SignalingMessage msg) {
        String id = msg.getMsgId() == null ? "unknown" : msg.getMsgId();
        return "reentry_" + id;
    }
}