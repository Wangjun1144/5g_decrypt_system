package com.example.procedure.parser;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条 packet 里 RRC 部分你关心的信息。
 * 后面你需要 C-RNTI、UEID 等，也可以直接加字段。
 */
@Data
// RRC 信息
public class RrcInfo {

    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;

    private String direction;   // UL / DL
    private String msgName;     // rrcSetupRequest / rrcSetup / rrcSetupComplete ...

    // 你关心的一些额外字段（先预留，没解析到就默认 null）
    private String randomValueHex;       // RRCSetupRequest 里的 randomValue
    private String establishmentCause;   // RRCSetupRequest 的 establishmentCause
    private String crnti;                // 来自 mac-nr.rnti

    private String integrityProtAlgorithm;
    private String cipheringAlgorithm;
    private boolean hasDedicatedNas;     // RRCSetupComplete 是否携带 NAS 容器

    // ⭐ 新增：记录每个命中字段对应的 JSON 路径（方便回溯）
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public Map<String, String> getFieldPaths() {
        return fieldPaths;
    }

    public void putFieldPath(String fieldKey, String path) {
        this.fieldPaths.put(fieldKey, path);
    }


    // ... 后面你想到什么再加
}
