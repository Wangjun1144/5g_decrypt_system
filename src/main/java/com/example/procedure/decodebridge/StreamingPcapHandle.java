package com.example.procedure.decodebridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StreamingPcapHandle implements AutoCloseable {

    private Path tempDir;
    private Path hexdumpFile;
    private Path pcapFile;
    private Integer dlt;
    private long byteLength;
    private boolean deleteOnClose = true;

    public Path getTempDir() {
        return tempDir;
    }

    public void setTempDir(Path tempDir) {
        this.tempDir = tempDir;
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

    public long getByteLength() {
        return byteLength;
    }

    public void setByteLength(long byteLength) {
        this.byteLength = byteLength;
    }

    public boolean isDeleteOnClose() {
        return deleteOnClose;
    }

    public void setDeleteOnClose(boolean deleteOnClose) {
        this.deleteOnClose = deleteOnClose;
    }

    @Override
    public void close() {
        if (!deleteOnClose) {
            return;
        }
        safeDelete(hexdumpFile);
        safeDelete(pcapFile);
        safeDelete(tempDir);
    }

    private void safeDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}