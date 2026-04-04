package com.example.procedure.infrastructure.dissection;

import com.example.procedure.infrastructure.capture.CapturedPacket;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable per-packet context inspired by Wireshark's packet_info.
 *
 * <p>This object carries frame-level metadata and protocol-trace information
 * while packet dissection progresses from one entry dissector to the next.</p>
 */
public class PacketContext {

    private final long packetIndex;
    private final Instant timestamp;
    private final int linkType;
    private final int capturedLength;
    private final int originalLength;
    private final Path sourceFile;
    private final long dataOffset;
    private final List<String> protocolTrace = new ArrayList<>();

    public PacketContext(CapturedPacket packet) {
        if (packet == null) {
            throw new IllegalArgumentException("packet must not be null");
        }
        this.packetIndex = packet.getPacketIndex();
        this.timestamp = packet.getTimestamp();
        this.linkType = packet.getLinkType();
        this.capturedLength = packet.getCapturedLength();
        this.originalLength = packet.getOriginalLength();
        this.sourceFile = packet.getSourceFile();
        this.dataOffset = packet.getDataOffset();
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

    public Path getSourceFile() {
        return sourceFile;
    }

    public long getDataOffset() {
        return dataOffset;
    }

    public void addProtocol(String protocolName) {
        if (protocolName == null || protocolName.isBlank()) {
            return;
        }
        protocolTrace.add(protocolName);
    }

    public List<String> getProtocolTrace() {
        return Collections.unmodifiableList(protocolTrace);
    }
}
