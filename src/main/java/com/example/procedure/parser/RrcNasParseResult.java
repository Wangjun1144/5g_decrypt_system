package com.example.procedure.parser;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条 packet 解析完后返回的结果：
 *  - rrcInfo：如果有 RRC，就在这里
 *  - nasList：这条包里出现的所有 nas-5gs（包括嵌套在 NAS message container 里的）
 */
@Data
public class RrcNasParseResult {

    private MacInfo macInfo;

    // ⭐ 新增：PDCP 信息
    private PdcpInfo pdcpInfo;

    // ⭐ NGAP：一帧里可能有多条 NGAP PDU
    private List<NgapInfo> ngapList = new ArrayList<>();


    private RrcInfo rrcInfo;


    private List<NasInfo> nasList = new ArrayList<>();

    // ⭐ 新增：Nausf UE Authentication Response 信息（http2:json）
    private NUARInfo nuarInfo;


    /** 这一帧里是否出现加密 NAS（有任意一个 NasInfo.encrypted=true 即可置 true） */
    private boolean nasEncrypted;

    /** 简单标一下加密层，目前就用 "NAS"，以后可以扩展 "PDCP" 等 */
    private String encryptedLayer;
}

