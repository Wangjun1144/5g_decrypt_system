package com.example.procedure.infrastructure.capture;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/**
 * Immutable snapshot of one packet record read from an offline capture file.
 */
public final class CapturedPacket {

    private final long packetIndex;
    private final Instant timestamp;
    private final int linkType;
    private final int capturedLength;
    private final int originalLength;
    private final byte[] rawBytes;
    private final Path sourceFile;
    private final long dataOffset;

    public CapturedPacket(
            long packetIndex,
            Instant timestamp,
            int linkType,
            int capturedLength,
            int originalLength,
            byte[] rawBytes,
            Path sourceFile,
            long dataOffset
    ) {
        this.packetIndex = packetIndex;
        this.timestamp = timestamp;
        this.linkType = linkType;
        this.capturedLength = capturedLength;
        this.originalLength = originalLength;
        this.rawBytes = rawBytes == null ? new byte[0] : Arrays.copyOf(rawBytes, rawBytes.length);
        this.sourceFile = sourceFile;
        this.dataOffset = dataOffset;
    }

    public long getPacketIndex() {
        return packetIndex;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getLinkType() {
        return linkType;
    }

    public int getCapturedLength() {
        return capturedLength;
    }

    public int getOriginalLength() {
        return originalLength;
    }

    public byte[] getRawBytes() {
        return Arrays.copyOf(rawBytes, rawBytes.length);
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public long getDataOffset() {
        return dataOffset;
    }
}
