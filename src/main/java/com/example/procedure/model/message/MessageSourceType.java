package com.example.procedure.model.message;

/**
 * Source categories for one signaling-message ingress request.
 *
 * These values describe where the message entered the application layer from,
 * so logging, tracing, and future ingress adapters can reason about the entry
 * path without depending on concrete transport implementations.
 */
public enum MessageSourceType {

    /**
     * The message came from pcap ingestion.
     */
    PCAP,

    /**
     * The message was submitted directly in-process, for example from tests or
     * a local caller.
     */
    DIRECT,

    /**
     * The message re-entered from a downstream stage such as decrypt retry or
     * another internal replay path.
     */
    REENTRY
}
