package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Request contract for streaming pcap decode.
 *
 * It carries the source pcap, the logical layers that should produce chain
 * results, the raw layers that should participate in strict sibling matching,
 * and the downstream signaling-message sink.
 */
public class PcapDecodeRequest {

    private final Path pcap;
    private final Set<String> wantedLayers;
    private final Set<String> enabledRawLayers;
    private final Consumer<SignalingMessage> messageConsumer;

    public PcapDecodeRequest(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> messageConsumer
    ) {
        this.pcap = pcap;
        this.wantedLayers = immutableCopy(wantedLayers);
        this.enabledRawLayers = immutableCopy(enabledRawLayers);
        this.messageConsumer = messageConsumer;
    }

    public static PcapDecodeRequest of(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> messageConsumer
    ) {
        return new PcapDecodeRequest(pcap, wantedLayers, enabledRawLayers, messageConsumer);
    }

    public Path getPcap() {
        return pcap;
    }

    public Set<String> getWantedLayers() {
        return wantedLayers;
    }

    public Set<String> getEnabledRawLayers() {
        return enabledRawLayers;
    }

    /**
     * Legacy alias kept for compatibility with older callers.
     */
    public Set<String> getWanted() {
        return getWantedLayers();
    }

    /**
     * Legacy alias kept for compatibility with older callers.
     */
    public Set<String> getEnabledRaw() {
        return getEnabledRawLayers();
    }

    public Consumer<SignalingMessage> getMessageConsumer() {
        return messageConsumer;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
