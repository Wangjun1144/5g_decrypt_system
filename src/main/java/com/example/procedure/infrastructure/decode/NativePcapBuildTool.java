package com.example.procedure.infrastructure.decode;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java pcap builder that converts a text2pcap-style hexdump into one pcap packet.
 */
@Component
public class NativePcapBuildTool implements PcapBuildTool {

    private static final int PCAP_MAGIC = 0xa1b2c3d4;
    private static final short PCAP_VERSION_MAJOR = 2;
    private static final short PCAP_VERSION_MINOR = 4;
    private static final int DEFAULT_SNAPLEN = 65535;

    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        if (hexdumpFile == null || !Files.exists(hexdumpFile)) {
            throw new IllegalArgumentException("hexdump file not found: " + hexdumpFile);
        }
        if (outPcap == null) {
            throw new IllegalArgumentException("outPcap must not be null");
        }
        if (dlt < 0) {
            throw new IllegalArgumentException("dlt must not be negative: " + dlt);
        }

        byte[] payload = parseHexdump(hexdumpFile);
        Path outputParent = outPcap.toAbsolutePath().normalize().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        Instant now = Instant.now();
        byte[] pcapBytes = buildPcapBytes(payload, dlt, now);
        Files.write(outPcap, pcapBytes);
        return outPcap;
    }

    static byte[] buildPcapBytes(byte[] payload, int dlt, Instant timestamp) {
        byte[] body = payload == null ? new byte[0] : payload;
        Instant ts = timestamp == null ? Instant.EPOCH : timestamp;
        int totalSize = 24 + 16 + body.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(PCAP_MAGIC);
        buffer.putShort(PCAP_VERSION_MAJOR);
        buffer.putShort(PCAP_VERSION_MINOR);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(DEFAULT_SNAPLEN);
        buffer.putInt(dlt);

        long epochSecond = ts.getEpochSecond();
        int micros = ts.getNano() / 1_000;
        buffer.putInt((int) epochSecond);
        buffer.putInt(micros);
        buffer.putInt(body.length);
        buffer.putInt(body.length);
        buffer.put(body);
        return buffer.array();
    }

    static byte[] parseHexdump(Path hexdumpFile) throws IOException {
        List<Byte> bytes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(hexdumpFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendHexdumpLine(bytes, line);
            }
        }

        byte[] out = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            out[i] = bytes.get(i);
        }
        return out;
    }

    private static void appendHexdumpLine(List<Byte> out, String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        String[] tokens = line.trim().split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        int start = looksLikeOffset(tokens[0]) ? 1 : 0;
        for (int i = start; i < tokens.length; i++) {
            String token = stripPunctuation(tokens[i]);
            if (!isByteToken(token)) {
                break;
            }
            out.add((byte) Integer.parseInt(token, 16));
        }
    }

    private static boolean looksLikeOffset(String token) {
        String normalized = stripPunctuation(token);
        return !normalized.isEmpty() && normalized.matches("[0-9a-fA-F]{1,8}");
    }

    private static String stripPunctuation(String token) {
        return token == null ? "" : token.replaceAll("[:|-]", "");
    }

    private static boolean isByteToken(String token) {
        return token != null && token.matches("[0-9a-fA-F]{2}");
    }
}
