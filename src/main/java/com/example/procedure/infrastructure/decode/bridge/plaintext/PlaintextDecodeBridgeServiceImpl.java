package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugDecodeArtifacts;
import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugPcapBuildResult;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Service
public class PlaintextDecodeBridgeServiceImpl implements PlaintextDecodeBridgeService {

    private final PlaintextToPcapService plaintextToPcapService;
    private final PcapJsonDecodeGateway pcapJsonDecodeGateway;

    public PlaintextDecodeBridgeServiceImpl(
            PlaintextToPcapService plaintextToPcapService,
            PcapJsonDecodeGateway pcapJsonDecodeGateway
    ) {
        this.plaintextToPcapService = plaintextToPcapService;
        this.pcapJsonDecodeGateway = pcapJsonDecodeGateway;
    }

    @Override
    public DebugDecodeArtifacts buildDebugArtifacts(PlaintextDecodeRequest request) throws Exception {
        DebugPcapBuildResult pcapResult = plaintextToPcapService.buildDebugPcap(request);

        String json = pcapJsonDecodeGateway.decodeToJson(pcapResult.getPcapFile());

        String baseName = stripExtension(pcapResult.getPcapFile().getFileName().toString());
        Path jsonFile = pcapResult.getWorkDir().resolve(baseName + ".json");
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

        DebugDecodeArtifacts result = new DebugDecodeArtifacts();
        result.setWorkDir(pcapResult.getWorkDir());
        result.setHexdumpFile(pcapResult.getHexdumpFile());
        result.setPcapFile(pcapResult.getPcapFile());
        result.setJsonFile(jsonFile);
        result.setDlt(pcapResult.getDlt());
        result.setByteLength(pcapResult.getByteLength());
        return result;
    }

    @Override
    public void streamDecodedJson(
            PlaintextDecodeRequest request,
            Consumer<InputStream> jsonConsumer
    ) throws Exception {
        if (jsonConsumer == null) {
            throw new IllegalArgumentException("jsonConsumer must not be null");
        }

        try (StreamingPcapHandle handle = plaintextToPcapService.buildStreamingPcap(request)) {
            pcapJsonDecodeGateway.decodeToJsonStream(handle.getPcapFile(), jsonConsumer);
        }
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? fileName : fileName.substring(0, idx);
    }
}
