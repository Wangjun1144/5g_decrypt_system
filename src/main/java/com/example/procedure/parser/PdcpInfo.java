package com.example.procedure.parser;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条 packet 里 PDCP 层你关心的信息。
 * 目前只关心：
 *  - pdcp-nr.signalling-data
 *  - pdcp-nr.mac
 */
@Data
public class PdcpInfo {

    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;

    boolean pdcpencrypted;
    /**
     * PDCP 信令数据，去掉冒号后的纯十六进制串：
     *  例如：b4:3f:7c:e7:0a:c5:76:a3 -> b43f7ce70ac576a3
     */
    private String signallingDataHex;

    private String decyptedTexHex;

    private String direction;   // UL / DL

    private String seqnum;

    public Integer getSeqNumInt() {
        if (seqnum == null) return null;
        String s = seqnum.trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    /**
     * PDCP MAC，去掉 0x 前缀后的十六进制串：
     *  例如：0x51a85e19 -> 51a85e19
     */
    private String macHex;

    /** 记录每个命中字段的 JSON 路径，方便回溯 */
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public void putFieldPath(String fieldKey, String path) {
        this.fieldPaths.put(fieldKey, path);
    }
}

