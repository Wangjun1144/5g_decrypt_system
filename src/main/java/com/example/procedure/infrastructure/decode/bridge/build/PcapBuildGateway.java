package com.example.procedure.infrastructure.decode.bridge.build;

import java.nio.file.Path;

/**
 * Build gateway for turning hexdump-oriented inputs into pcap files.
 *
 * This boundary keeps upper layers independent from the concrete text2pcap
 * invocation details and leaves room for future local, remote, or async
 * build implementations.
 */
public interface PcapBuildGateway {

    /**
     * Builds a pcap file from one hexdump source file.
     *
     * @param hexdumpFile input hexdump file
     * @param dlt target data-link type
     * @param outPcap output pcap path
     * @return built pcap path
     * @throws Exception when the build fails
     */
    Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception;
}
