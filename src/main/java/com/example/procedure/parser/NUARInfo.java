package com.example.procedure.parser;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * http2:json 场景下，Nausf_UEAuthentication_Authenticate Response 信息。
 */
@Data
public class NUARInfo {

    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;

    /** 消息名：固定 Nausf_UEAuthentication_AuthenticateResponse */
    private String msgName;

    /** 原始 supi，例如 imsi-001010000000001 */
    private String supi;

    /** 提取后的 IMSI，例如 001010000000001 */
    private String imsi;

    /** Kseaf 十六进制串 */
    private String kseafHex;

    /** authResult 值，例如 AUTHENTICATION_SUCCESS */
    private String authResult;

    /** 记录字段对应的 JSON 路径，方便回溯 */
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public void putFieldPath(String key, String path) {
        this.fieldPaths.put(key, path);
    }
}
