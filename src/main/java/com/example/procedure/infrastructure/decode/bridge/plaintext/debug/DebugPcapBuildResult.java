package com.example.procedure.infrastructure.decode.bridge.plaintext.debug;

import java.nio.file.Path;

/**
 * Debug result produced by plaintext-to-pcap build flows.
 */
public class DebugPcapBuildResult {

    private Path workDir;
    private Path hexdumpFile;
    private Path pcapFile;
    private Integer dlt;
    private String normalizedHex;
    private long byteLength;

    public Path getWorkDir() {
        return workDir;
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    public Path getHexdumpFile() {
        return hexdumpFile;
    }

    public void setHexdumpFile(Path hexdumpFile) {
        this.hexdumpFile = hexdumpFile;
    }

    public Path getPcapFile() {
        return pcapFile;
    }

    public void setPcapFile(Path pcapFile) {
        this.pcapFile = pcapFile;
    }

    public Integer getDlt() {
        return dlt;
    }

    public void setDlt(Integer dlt) {
        this.dlt = dlt;
    }

    public String getNormalizedHex() {
        return normalizedHex;
    }

    public void setNormalizedHex(String normalizedHex) {
        this.normalizedHex = normalizedHex;
    }

    public long getByteLength() {
        return byteLength;
    }

    public void setByteLength(long byteLength) {
        this.byteLength = byteLength;
    }
}
