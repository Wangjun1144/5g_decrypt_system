package com.example.procedure.infrastructure.decode.bridge.plaintext.debug;

import java.nio.file.Path;

/**
 * Debug artifact bundle produced by plaintext decode flows.
 */
public class DebugDecodeArtifacts {

    private Path workDir;
    private Path hexdumpFile;
    private Path pcapFile;
    private Path jsonFile;
    private Integer dlt;
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

    public Path getJsonFile() {
        return jsonFile;
    }

    public void setJsonFile(Path jsonFile) {
        this.jsonFile = jsonFile;
    }

    public Integer getDlt() {
        return dlt;
    }

    public void setDlt(Integer dlt) {
        this.dlt = dlt;
    }

    public long getByteLength() {
        return byteLength;
    }

    public void setByteLength(long byteLength) {
        this.byteLength = byteLength;
    }
}
