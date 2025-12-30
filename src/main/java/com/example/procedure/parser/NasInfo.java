package com.example.procedure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对应一个 "nas-5gs" 节点。
 * 现在只存：
 *  - nasNode：原始 JSON 节点（你后面想要什么字段，自己从这里挖都行）
 *  - fullNasPduHex：整条 NAS PDU 的十六进制
 *  - cipherTextHex：密文部分十六进制（现在先留空/占位）
 */
@Data
public class NasInfo {

    /** 在当前帧中的解析顺序号（从 1 开始自增） */
    private int sequence;

    private JsonNode nasNode;        // 原始逻辑节点（你原来就有）
    /** 完整 nas-5gs_raw 的十六进制（去掉冒号） */
    private String fullNasPduHex;

    /** 截完安全头之后剩下的密文部分 */
    private String cipherTextHex;

    private String decyptedTexHex;


    /** 该 NAS 是否已经加密（security protected） */
    private boolean encrypted;

    // ---- Security protected NAS 里的一些字段 ----
    private String epd;                // nas-5gs.epd，原始字符串（"126"）
    private String spareHalfOctet;     // nas-5gs.spare_half_octet
    private String securityHeaderType; // nas-5gs.security_header_type
    private String msgAuthCodeHex;     // nas-5gs.msg_auth_code（0x 去掉后）
    private String seqNo;              // nas-5gs.seq_no

    public Integer getSeqNoInt() {
        if (seqNo == null) return null;
        String s = seqNo.trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** nas-5gs.mm.message_type */
    private String mmMessageType;

    /** nas-5gs.mm.nas_sec_algo_enc */
    private String nas_cipheringAlgorithm;

    private String nas_integrityProtAlgorithm;

    /** e212.guami.mcc */
    private String guamiMcc;

    /** e212.guami.mnc */
    private String guamiMnc;

    /** 3gpp.tmsi */
    private String tmsi;

    /** nas-5gs.mm.5gs_reg_type */
    private String regType5gs;

    // ⭐ 新增：NAS 字段对应的路径
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public Map<String, String> getFieldPaths() {
        return fieldPaths;
    }

    public void putFieldPath(String fieldKey, String path) {
        this.fieldPaths.put(fieldKey, path);
    }
    // ... 后面想到再加
}

