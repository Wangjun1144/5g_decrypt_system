package com.example.procedure.infrastructure.dissection;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.dissection.registry.LinkTypeDissectorRegistry;
import org.springframework.stereotype.Component;

/**
 * Entry point for packet dissection, similar to Wireshark's frame-first flow.
 */
@Component
public class FrameDissector {

    private final LinkTypeDissectorRegistry registry;

    public FrameDissector(LinkTypeDissectorRegistry registry) {
        this.registry = registry;
    }

    public DissectionResult dissect(CapturedPacket packet) throws Exception {
        if (packet == null) {
            throw new IllegalArgumentException("packet must not be null");
        }

        PacketContext context = new PacketContext(packet);
        context.addProtocol("frame");

        PacketDissector entry = registry.lookup(packet.getLinkType())
                .orElseThrow(() -> new IllegalStateException(
                        "No entry dissector registered for linkType=" + packet.getLinkType()
                ));
        return entry.dissect(PacketBuffer.wrap(packet.getRawBytes()), context);
    }
}
