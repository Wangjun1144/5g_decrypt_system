package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.FrameDissector;
import com.example.procedure.infrastructure.dissection.assemble.NativeNasSignalingMessageAssembler;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pcap.PcapDecodeCommand;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Isolated native NAS pcap decode path.
 *
 * <p>This gateway intentionally stays out of the current Spring-selected
 * production chain until the native NAS path is broad enough to replace the
 * tshark-based implementation.</p>
 */
public class NativeNasPcapDecodeGateway implements PcapDecodeGateway {

    private final PcapFileReader pcapFileReader;
    private final FrameDissector frameDissector;
    private final NativeNasSignalingMessageAssembler signalingMessageAssembler;

    public NativeNasPcapDecodeGateway(
            PcapFileReader pcapFileReader,
            FrameDissector frameDissector,
            NativeNasSignalingMessageAssembler signalingMessageAssembler
    ) {
        this.pcapFileReader = Objects.requireNonNull(pcapFileReader, "pcapFileReader must not be null");
        this.frameDissector = Objects.requireNonNull(frameDissector, "frameDissector must not be null");
        this.signalingMessageAssembler = Objects.requireNonNull(
                signalingMessageAssembler,
                "signalingMessageAssembler must not be null"
        );
    }

    @Override
    public void decode(PcapDecodeCommand request) throws Exception {
        validateRequest(request);
        if (!wantsNas(request.getWantedLayers())) {
            return;
        }

        List<CapturedPacket> packets = pcapFileReader.readAll(request.getPcap());
        for (CapturedPacket packet : packets) {
            DissectionResult result = frameDissector.dissect(packet);
            if (!"nas-5gs".equals(result.getEntryProtocol())) {
                continue;
            }
            SignalingMessage message = signalingMessageAssembler.assemble(packet, result);
            request.getMessageConsumer().accept(message);
        }
    }

    private void validateRequest(PcapDecodeCommand request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getPcap(), "pcap must not be null");
        Objects.requireNonNull(request.getWantedLayers(), "wantedLayers must not be null");
        Objects.requireNonNull(request.getEnabledRawLayers(), "enabledRawLayers must not be null");
        Objects.requireNonNull(request.getMessageConsumer(), "messageConsumer must not be null");
    }

    private boolean wantsNas(Set<String> wantedLayers) {
        if (wantedLayers == null || wantedLayers.isEmpty()) {
            return true;
        }
        for (String layer : wantedLayers) {
            if (layer == null) {
                continue;
            }
            String normalized = layer.trim().toLowerCase(Locale.ROOT);
            if ("nas".equals(normalized) || "nas-5gs".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
