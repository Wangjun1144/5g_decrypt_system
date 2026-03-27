package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Set;

/**
 * Application-facing entry contract for one pcap batch ingestion run.
 *
 * Upstream callers should depend on this boundary instead of directly invoking
 * lower-level decode services. The processor coordinates one complete pcap
 * ingestion flow from decode to message-pipeline forwarding.
 */
public interface PcapBatchProcessor {

    /**
     * Process one application-layer pcap batch request.
     *
     * @param request batch process request
     * @throws Exception when the batch run fails
     */
    void process(PcapBatchProcessRequest request) throws Exception;

    /**
     * Legacy convenience overload kept for compatibility with older callers.
     *
     * @param pcap source pcap path
     * @param wanted logical layers to keep
     * @param enabledRaw raw layers that participate in strict matching
     * @throws Exception when the batch run fails
     */
    default void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        process(PcapBatchProcessRequest.of(pcap, wanted, enabledRaw));
    }
}
