package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class PlaintextPacketParseBridgeServiceImpl implements PlaintextPacketParseBridgeService {

    private final PlaintextDecodeBridgeService plaintextDecodeBridgeService;
    private final PlaintextToPcapService plaintextToPcapService;
    private final PcapParseBridgeService pcapParseBridgeService;

    public PlaintextPacketParseBridgeServiceImpl(PlaintextDecodeBridgeService plaintextDecodeBridgeService,
                                                 PlaintextToPcapService plaintextToPcapService,
                                                 PcapParseBridgeService pcapParseBridgeService) {
        this.plaintextDecodeBridgeService = plaintextDecodeBridgeService;
        this.plaintextToPcapService = plaintextToPcapService;
        this.pcapParseBridgeService = pcapParseBridgeService;
    }

    @Override
    public DebugDecodeArtifacts debugBuildAndParse(PlaintextDecodeRequest request,
                                                   Set<String> wanted,
                                                   Set<String> enabledRaw,
                                                   Consumer<SignalingMessage> messageConsumer) throws Exception {
        Objects.requireNonNull(messageConsumer, "messageConsumer must not be null");

        DebugDecodeArtifacts artifacts = plaintextDecodeBridgeService.buildDebugArtifacts(request);

        pcapParseBridgeService.parsePcap(
                artifacts.getPcapFile(),
                wanted,
                enabledRaw,
                messageConsumer
        );

        return artifacts;
    }

    @Override
    public void streamBuildAndParse(PlaintextDecodeRequest request,
                                    Set<String> wanted,
                                    Set<String> enabledRaw,
                                    Consumer<SignalingMessage> messageConsumer) throws Exception {
        Objects.requireNonNull(messageConsumer, "messageConsumer must not be null");

        try (StreamingPcapHandle handle = plaintextToPcapService.buildStreamingPcap(request)) {
            pcapParseBridgeService.parsePcap(
                    handle.getPcapFile(),
                    wanted,
                    enabledRaw,
                    messageConsumer
            );
        }
    }
}