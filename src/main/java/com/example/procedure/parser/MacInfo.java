package com.example.procedure.parser;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条 packet 里 MAC 层你关心的信息。
 * 目前只关心：
 *  - mac-nr.rnti
 *  - mac-nr.rnti-type
 * 后面要加 HARQ id、direction 等字段都可以在这里扩展。
 */
@Data
public class MacInfo {
    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;

    /** C-RNTI 或其它类型的 RNTI，对应 mac-nr.rnti */
    private String rnti;

    /** RNTI 类型，对应 mac-nr.rnti-type */
    private String rntiType;

    /** 记录每个命中字段的 JSON 路径，方便回溯 */
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public void putFieldPath(String fieldKey, String path) {
        this.fieldPaths.put(fieldKey, path);
    }
}
