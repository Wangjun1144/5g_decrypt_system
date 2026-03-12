package com.example.procedure.model;

import com.example.procedure.model.tree.MessageTree;
import com.example.procedure.parser.*;
import lombok.Data;

import java.util.List;

@Data
public class SignalingMessage {

    private String msgId;
    private String ueId;
    private String iface;
    private String direction;
    private String protocolLayer;
    private String msgType;
    private long timestamp;
    private long frameNo;

    private MessagePayload payload;

    private MacInfo macInfo;
    private PdcpInfo pdcpInfo;
    private RrcInfo rrcInfo;
    private List<NgapInfo> ngapInfoList;
    private NUARInfo nuarInfo;
    private List<NasInfo> nasList;

    private Boolean encrypted;
    private String encryptedType;

    private String decryptPlainHex;
    private String decryptMacHex;
    private boolean decrypted;
    private Integer decryptDepth;
    private String decryptPath;

    private String decryptTargetLayer;
    private Integer decryptTargetNasIndex; // 兼容旧逻辑，可逐步废弃

    /** 新增：本轮解密真正针对的原始节点 ID */
    private String decryptTargetNodeId;

    /**
     * 新增：
     * 回流消息来自哪个原始节点。
     * 它不是长期业务字段，主要用于 merge/graft。
     */
    private String reentrySourceNodeId;

    private MessageTree messageTree;
}