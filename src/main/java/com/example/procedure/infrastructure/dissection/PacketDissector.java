package com.example.procedure.infrastructure.dissection;

/**
 * Minimal packet dissector boundary inspired by Wireshark's dissector model.
 */
public interface PacketDissector {

    /**
     * Registration metadata for this dissector.
     */
    ProtocolRegistration registration();

    default String protocolName() {
        return registration().filterName();
    }

    /**
     * Dissects the current packet buffer within the given context.
     */
    DissectionResult dissect(PacketBuffer buffer, PacketContext context) throws Exception;
}
