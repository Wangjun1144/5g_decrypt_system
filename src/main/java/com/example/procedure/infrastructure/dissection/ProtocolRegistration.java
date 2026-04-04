package com.example.procedure.infrastructure.dissection;

/**
 * Minimal protocol registration record inspired by Wireshark's
 * proto_register_protocol metadata.
 */
public record ProtocolRegistration(
        String displayName,
        String shortName,
        String filterName
) {
}
