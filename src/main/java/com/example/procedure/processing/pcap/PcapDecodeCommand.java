package com.example.procedure.processing.pcap;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Internal command that describes one pcap decode request for the processing boundary.
 */
public class PcapDecodeCommand {

    private final Path pcap;
    private final Set<String> wantedLayers;
    private final Set<String> enabledRawLayers;
    private final Consumer<SignalingMessage> messageConsumer;

    public PcapDecodeCommand(
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

    public static PcapDecodeCommand of(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> messageConsumer
    ) {
        return new PcapDecodeCommand(pcap, wantedLayers, enabledRawLayers, messageConsumer);
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
