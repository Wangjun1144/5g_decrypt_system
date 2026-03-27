package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Application-layer request for one pcap batch ingestion run.
 *
 * It carries the source pcap together with the logical layers that should be
 * preserved and the raw layers that should participate in strict sibling
 * matching during downstream streaming decode.
 */
public class PcapBatchProcessRequest {

    private final Path pcap;
    private final Set<String> wantedLayers;
    private final Set<String> enabledRawLayers;

    public PcapBatchProcessRequest(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers
    ) {
        this.pcap = pcap;
        this.wantedLayers = immutableCopy(wantedLayers);
        this.enabledRawLayers = immutableCopy(enabledRawLayers);
    }

    public static PcapBatchProcessRequest of(
            Path pcap,
            Set<String> wantedLayers,
            Set<String> enabledRawLayers
    ) {
        return new PcapBatchProcessRequest(pcap, wantedLayers, enabledRawLayers);
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
     * Legacy alias kept for compatibility with existing callers.
     */
    public Set<String> getWanted() {
        return getWantedLayers();
    }

    /**
     * Legacy alias kept for compatibility with existing callers.
     */
    public Set<String> getEnabledRaw() {
        return getEnabledRawLayers();
    }

    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
