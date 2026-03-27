package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.build.PcapBuildGateway;
import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugPcapBuildResult;
import com.example.procedure.infrastructure.decode.HexCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PlaintextToPcapServiceImpl implements PlaintextToPcapService {

    private final HexCodec hexCodec;
    private final PcapBuildGateway pcapBuildGateway;
    private final DltResolver dltResolver;

    public PlaintextToPcapServiceImpl(
            HexCodec hexCodec,
            PcapBuildGateway pcapBuildGateway,
            DltResolver dltResolver
    ) {
        this.hexCodec = hexCodec;
        this.pcapBuildGateway = pcapBuildGateway;
        this.dltResolver = dltResolver;
    }

    @Override
    public DebugPcapBuildResult buildDebugPcap(PlaintextDecodeRequest request) throws Exception {
        validateRequest(request);

        int dlt = dltResolver.resolve(request);
        String normalizedHex = normalizeHex(request.getPlainHex());

        byte[] bytes = hexCodec.decodeHex(normalizedHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        String base = buildBaseName(request);
        Path workDir = Path.of("runtime", "plaintext_decode", base);
        Files.createDirectories(workDir);

        Path hexdumpFile = workDir.resolve(base + ".txt");
        Path pcapFile = workDir.resolve(base + ".pcap");

        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);
        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);

        if (!request.isKeepHexdumpFile()) {
            try {
                Files.deleteIfExists(hexdumpFile);
                hexdumpFile = null;
            } catch (Exception ignored) {
            }
        }

        DebugPcapBuildResult result = new DebugPcapBuildResult();
        result.setWorkDir(workDir);
        result.setHexdumpFile(hexdumpFile);
        result.setPcapFile(pcapFile);
        result.setDlt(dlt);
        result.setNormalizedHex(normalizedHex);
        result.setByteLength(bytes.length);
        return result;
    }

    @Override
    public StreamingPcapHandle buildStreamingPcap(PlaintextDecodeRequest request) throws Exception {
        validateRequest(request);

        int dlt = dltResolver.resolve(request);
        String normalizedHex = normalizeHex(request.getPlainHex());

        byte[] bytes = hexCodec.decodeHex(normalizedHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        Path tempDir = Files.createTempDirectory("plaintext_pcap_");
        Path hexdumpFile = tempDir.resolve("payload.txt");
        Path pcapFile = tempDir.resolve("payload.pcap");

        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);
        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);

        StreamingPcapHandle handle = new StreamingPcapHandle();
        handle.setTempDir(tempDir);
        handle.setHexdumpFile(hexdumpFile);
        handle.setPcapFile(pcapFile);
        handle.setDlt(dlt);
        handle.setByteLength(bytes.length);
        return handle;
    }

    private void validateRequest(PlaintextDecodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PlaintextDecodeRequest must not be null");
        }

        String normalizedHex = normalizeHex(request.getPlainHex());
        if (normalizedHex.isBlank()) {
            throw new IllegalArgumentException("plainHex must not be blank");
        }

        if ((normalizedHex.length() & 1) != 0) {
            throw new IllegalArgumentException("plainHex length must be even after normalization");
        }
    }

    private String normalizeHex(String plainHex) {
        return plainHex == null ? "" : plainHex.replaceAll("[:\\s]", "");
    }

    private String buildBaseName(PlaintextDecodeRequest request) {
        String trace = safeToken(request.getTraceId(), "noTrace");
        String msg = safeToken(request.getSourceMsgId(), "noMsg");
        String ue = safeToken(request.getUeId(), "noUe");
        return trace + "_" + ue + "_" + msg + "_" + System.currentTimeMillis();
    }

    private String safeToken(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
