package com.example.procedure.parser;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条 packet 里 NGAP 部分你关心的信息：
 *  - PDU 类型：initiating / successfulOutcome / unsuccessfulOutcome
 *  - 消息类型：UplinkNASTransport / InitialContextSetup / NGSetup 等
 *  - procedureCode / criticality 等附加字段
 */
@Data
public class NgapInfo {

    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;


    /** initiatingMessage / successfulOutcome / unsuccessfulOutcome */
    private String pduType;

    /** 消息类型，例如：UplinkNASTransport / InitialContextSetup / NGSetup ... */
    private String msgName;

    /** ⭐ NGAP 安全相关：SecurityKey（去掉冒号后的纯 hex） */
    private String securityKeyHex;

    /** ⭐ RAN UE NGAP ID */
    private String ranUeNgapId;

    private String direction;


    /** 记录每个字段的 JSON 路径，方便回溯 */
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public void putFieldPath(String key, String path) {
        this.fieldPaths.put(key, path);
    }
}
