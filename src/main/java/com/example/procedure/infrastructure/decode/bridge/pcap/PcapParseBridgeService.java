package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.processing.pcap.PcapDecodeCommand;
import com.example.procedure.processing.pcap.PcapDecodePort;
import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bridge service that exposes pcap parsing to upper layers in decodebridge
 * terms rather than tshark implementation details.
 */
public interface PcapParseBridgeService extends PcapDecodePort {

    /**
     * Parse one pcap decode request through the pcap bridge boundary.
     *
     * @param request decode request
     * @throws Exception when decode or streaming parse fails
     */
    void parse(PcapDecodeCommand request) throws Exception;

    /**
     * Convenience overload for callers that construct the command inline.
     */
    default void parsePcap(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> messageConsumer
    ) throws Exception {
        parse(PcapDecodeCommand.of(pcap, wantedLayers, enabledRawLayers, messageConsumer));
    }
}
