package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bridge service that exposes pcap parsing to upper layers in decodebridge
 * terms rather than tshark implementation details.
 */
public interface PcapParseBridgeService {

    /**
     * Parse one pcap decode request through the pcap bridge boundary.
     *
     * @param request decode request
     * @throws Exception when decode or streaming parse fails
     */
    void parse(PcapDecodeRequest request) throws Exception;

    /**
     * Convenience overload for callers that construct the request inline.
     *
     * @param pcap source pcap path
     * @param wantedLayers logical layers that should produce chain results
     * @param enabledRawLayers raw layers that should participate in strict matching
     * @param messageConsumer downstream signaling-message sink
     * @throws Exception when decode or streaming parse fails
     */
    default void parsePcap(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> messageConsumer
    ) throws Exception {
        parse(PcapDecodeRequest.of(pcap, wantedLayers, enabledRawLayers, messageConsumer));
    }
}
