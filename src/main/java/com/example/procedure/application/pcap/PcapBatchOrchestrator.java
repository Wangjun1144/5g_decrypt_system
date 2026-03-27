package com.example.procedure.application.pcap;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.application.message.SignalingMessageIngressRequest;
import com.example.procedure.application.message.SignalingMessagePipeline;
import com.example.procedure.infrastructure.decode.bridge.pcap.PcapDecodeRequest;
import com.example.procedure.infrastructure.decode.bridge.pcap.PcapParseBridgeService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Application-layer batch orchestrator for pcap ingestion.
 *
 * Current responsibilities:
 * 1. Parse one pcap file through the decode bridge.
 * 2. Forward each decoded signaling message into the application message pipeline.
 * 3. Keep top-level pcap-stage logging and error mapping at the application edge.
 */
@Service
public class PcapBatchOrchestrator implements PcapBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(PcapBatchOrchestrator.class);

    private final PcapParseBridgeService pcapParseBridgeService;
    private final SignalingMessagePipeline signalingMessagePipeline;

    /**
     * Creates the pcap batch orchestrator.
     *
     * @param pcapParseBridgeService bridge for parsing pcap into signaling messages
     * @param signalingMessagePipeline application pipeline for one signaling message
     */
    public PcapBatchOrchestrator(
            PcapParseBridgeService pcapParseBridgeService,
            SignalingMessagePipeline signalingMessagePipeline
    ) {
        this.pcapParseBridgeService = pcapParseBridgeService;
        this.signalingMessagePipeline = signalingMessagePipeline;
    }

    /**
     * Processes one pcap batch request from the application edge.
     */
    @Override
    public void process(PcapBatchProcessRequest request) throws Exception {
        Path pcap = request.getPcap();
        Set<String> wanted = request.getWantedLayers();
        Set<String> enabledRaw = request.getEnabledRawLayers();

        logBatchEntry(pcap, wanted, enabledRaw);

        String batchCorrelationId = buildBatchCorrelationId(pcap);

        parsePcapToMessages(
                pcap,
                wanted,
                enabledRaw,
                msg -> forwardToMessagePipeline(msg, pcap, batchCorrelationId)
        );

        logBatchExit(pcap);
    }

    /**
     * Parses one pcap file into a stream of signaling messages through the decode bridge.
     */
    private void parsePcapToMessages(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw,
            Consumer<SignalingMessage> sink
    ) throws Exception {
        log.debug("Pcap stage[decode-stream] enter: pcap={}, wanted={}, enabledRaw={}",
                StageLogRefs.pcap(pcap),
                StageLogRefs.size(wanted),
                StageLogRefs.size(enabledRaw));

        try {
            pcapParseBridgeService.parse(
                    PcapDecodeRequest.of(pcap, wanted, enabledRaw, sink)
            );

            log.debug("Pcap stage[decode-stream] exit: pcap={}", StageLogRefs.pcap(pcap));
        } catch (ApplicationStageException e) {
            throw e;
        } catch (Exception e) {
            throw ApplicationStageErrors.forPcap(
                    "pcap-decode",
                    pcap,
                    "Failed while decoding pcap through decode bridge",
                    e
            );
        }
    }

    /**
     * Forwards one decoded signaling message into the application message pipeline.
     */
    private void forwardToMessagePipeline(
            SignalingMessage msg,
            Path pcap,
            String batchCorrelationId
    ) {
        log.debug("Pcap stage[forward-message] enter: {}, correlationId={}",
                StageLogRefs.message(msg),
                batchCorrelationId);

        try {
            signalingMessagePipeline.process(
                    SignalingMessageIngressRequest.fromPcap(
                            msg,
                            pcap == null ? null : pcap.toString(),
                            batchCorrelationId
                    )
            );

            log.debug("Pcap stage[forward-message] exit: {}, correlationId={}",
                    StageLogRefs.message(msg),
                    batchCorrelationId);
        } catch (ApplicationStageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-pipeline",
                    msg,
                    "Failed while forwarding signaling message into message pipeline",
                    e
            );
        }
    }

    /**
     * Builds a batch correlation id for one pcap ingestion run.
     */
    private String buildBatchCorrelationId(Path pcap) {
        String name = pcap == null ? "pcap" : pcap.getFileName().toString();
        return "pcap-" + name + "-" + UUID.randomUUID();
    }

    /**
     * Writes the pcap batch entry log.
     */
    private void logBatchEntry(Path pcap, Set<String> wanted, Set<String> enabledRaw) {
        log.info("Pcap batch enter: pcap={}, wanted={}, enabledRaw={}",
                StageLogRefs.pcap(pcap),
                StageLogRefs.size(wanted),
                StageLogRefs.size(enabledRaw));
    }

    /**
     * Writes the pcap batch exit log.
     */
    private void logBatchExit(Path pcap) {
        log.info("Pcap batch exit: pcap={}", StageLogRefs.pcap(pcap));
    }
}
