package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.processing.pcap.PcapDecodeCommand;

/**
 * Decode-bridge boundary for turning one pcap request into a stream of parsed
 * signaling messages.
 */
public interface PcapDecodeGateway {

    /**
     * Decode one pcap request.
     *
     * @param request decode request
     * @throws Exception when decode or downstream streaming parse fails
     */
    void decode(PcapDecodeCommand request) throws Exception;
}
