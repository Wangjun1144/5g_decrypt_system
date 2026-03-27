package com.example.procedure.infrastructure.parser.streaming.parser;

/**
 * High-level frame payload categories recognized during streaming parse.
 *
 * These values are used when parser internals need a compact protocol-layer
 * category without depending directly on downstream message-model types.
 */
public enum FrameType {
    MAC,
    PDCP,
    RRC,
    NAS,
    NGAP,
    NUAR
}
