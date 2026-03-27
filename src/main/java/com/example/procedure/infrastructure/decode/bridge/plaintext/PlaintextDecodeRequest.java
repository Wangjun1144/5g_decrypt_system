package com.example.procedure.infrastructure.decode.bridge.plaintext;

import lombok.Data;

@Data
public class PlaintextDecodeRequest {

    private String plainHex;
    private Integer dlt;
    private String protocolHint;
    private String direction;
    private String iface;
    private String ueId;
    private String traceId;
    private String sourceMsgId;
    private boolean keepHexdumpFile = true;
    private String sourceNodeId;

    public String getPlainHex() {
        return plainHex;
    }

    public void setPlainHex(String plainHex) {
        this.plainHex = plainHex;
    }

    public Integer getDlt() {
        return dlt;
    }

    public void setDlt(Integer dlt) {
        this.dlt = dlt;
    }

    public String getProtocolHint() {
        return protocolHint;
    }

    public void setProtocolHint(String protocolHint) {
        this.protocolHint = protocolHint;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getIface() {
        return iface;
    }

    public void setIface(String iface) {
        this.iface = iface;
    }

    public String getUeId() {
        return ueId;
    }

    public void setUeId(String ueId) {
        this.ueId = ueId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSourceMsgId() {
        return sourceMsgId;
    }

    public void setSourceMsgId(String sourceMsgId) {
        this.sourceMsgId = sourceMsgId;
    }

    public boolean isKeepHexdumpFile() {
        return keepHexdumpFile;
    }

    public void setKeepHexdumpFile(boolean keepHexdumpFile) {
        this.keepHexdumpFile = keepHexdumpFile;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }
}
