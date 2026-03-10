package com.example.procedure.decodebridge;

public class PlaintextDecodeRequest {

    /**
     * 解密后的明文 hex，允许带空格/冒号/换行
     */
    private String plainHex;

    /**
     * 已知 DLT 时可直接指定；为空则走 DltResolver
     */
    private Integer dlt;

    /**
     * 协议提示，例如：
     * RRC_UL_DCCH / RRC_DL_DCCH / NAS_5GS / NGAP
     */
    private String protocolHint;

    /**
     * UL / DL
     */
    private String direction;

    /**
     * Uu / N2 / Xn
     */
    private String iface;

    /**
     * 便于追踪
     */
    private String ueId;

    /**
     * 一次处理链追踪号
     */
    private String traceId;

    /**
     * 原始消息ID
     */
    private String sourceMsgId;

    /**
     * 调试时是否保留 hexdump 文件
     */
    private boolean keepHexdumpFile = true;

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
}