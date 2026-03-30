package com.example.procedure.processing.pcap;

/**
 * Processing-layer port for decoding one pcap stream into signaling messages.
 */
public interface PcapDecodePort {

    void parse(PcapDecodeCommand command) throws Exception;
}
