package com.example.procedure.model;

import com.example.procedure.parser.*;
import lombok.Data;

import java.util.List;

/**
 * 单条信令记录（可以看作 process_record 的 Java 版本输入）
 */
@Data
public class SignalingMessage {

    private String msgId;
    /** UE 统一标识（你已经在前面做完 SUPI/C-RNTI 关联后得到的 ueId） */
    private String ueId;

    /** 接口：Uu / N2 / Xn / F1 / E1 等 */
    private String iface;

    /** 上下行：UL / DL */
    private String direction;

    /** 协议层：RRC / NAS / NGAP ... */
    private String protocolLayer;

    /** 统一后的消息类型（建议你自己枚举/映射，比如 RRC_SETUP_REQUEST 等） */
    private String msgType;

    /** 时间戳，用于排序和超时判断 */
    private long timestamp;

    /** 可选：帧号 / 序号，方便调试 */
    private long frameNo;

    private MessagePayload payload;

    /** 本帧的 MAC / PDCP / RRC / NGAP / NAUSF 信息（如果有的话） */
    private MacInfo macInfo;
    private PdcpInfo pdcpInfo;
    private RrcInfo rrcInfo;
    private List<NgapInfo> ngapInfoList;
    private NUARInfo nuarInfo;

    /** 本条信令中承载的所有 nas-5gs（可能为 0 个或多个） */
    private List<NasInfo> nasList;


    /** 是否加密（NAS 或 PDCP 任意一层加密都算） */
    private Boolean encrypted;

    /** 加密类型/来源：NONE / NAS / PDCP / NAS+PDCP */
    private String encryptedType;


    /** 解密成功后的明文（例如 hex 或 json 字符串，按你服务返回格式存） */
    private String decryptPlainHex;

    /** 解密相关的 MAC（可存服务返回的 mac 或者本次校验使用的 mac） */
    private String decryptMacHex;



}
