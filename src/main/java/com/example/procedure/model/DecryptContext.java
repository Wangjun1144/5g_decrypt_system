package com.example.procedure.model;

/**
 * 解密/回流上下文
 *
 * 这些字段并不是“抓包消息天然拥有”的属性，
 * 而是消息在处理过程中附加上的状态。
 *
 * 将其从 SignalingMessage 中拆出来后，
 * 可以显著降低主模型的混乱度。
 */
public class DecryptContext {

    /** 是否被识别为加密消息 */
    private Boolean encrypted;

    /** 加密类型：NAS / PDCP / NAS+PDCP / NONE */
    private String encryptedType;

    /** 解密得到的明文 hex */
    private String decryptPlainHex;

    /** 解密得到的明文 MAC（如果有） */
    private String decryptMacHex;

    /** 解密目标层，例如 NAS / PDCP */
    private String decryptTargetLayer;

    /** message tree 中待 graft 的目标节点 ID */
    private String decryptTargetNodeId;

    /** 本次 reentry 的源节点 ID */
    private String reentrySourceNodeId;

    /** 记录解密路径，例如 NAS->PDCP */
    private String decryptPath;

    /** 当前解密递归深度 */
    private Integer decryptDepth;

    public Boolean getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getEncryptedType() {
        return encryptedType;
    }

    public void setEncryptedType(String encryptedType) {
        this.encryptedType = encryptedType;
    }

    public String getDecryptPlainHex() {
        return decryptPlainHex;
    }

    public void setDecryptPlainHex(String decryptPlainHex) {
        this.decryptPlainHex = decryptPlainHex;
    }

    public String getDecryptMacHex() {
        return decryptMacHex;
    }

    public void setDecryptMacHex(String decryptMacHex) {
        this.decryptMacHex = decryptMacHex;
    }

    public String getDecryptTargetLayer() {
        return decryptTargetLayer;
    }

    public void setDecryptTargetLayer(String decryptTargetLayer) {
        this.decryptTargetLayer = decryptTargetLayer;
    }

    public String getDecryptTargetNodeId() {
        return decryptTargetNodeId;
    }

    public void setDecryptTargetNodeId(String decryptTargetNodeId) {
        this.decryptTargetNodeId = decryptTargetNodeId;
    }

    public String getReentrySourceNodeId() {
        return reentrySourceNodeId;
    }

    public void setReentrySourceNodeId(String reentrySourceNodeId) {
        this.reentrySourceNodeId = reentrySourceNodeId;
    }

    public String getDecryptPath() {
        return decryptPath;
    }

    public void setDecryptPath(String decryptPath) {
        this.decryptPath = decryptPath;
    }

    public Integer getDecryptDepth() {
        return decryptDepth;
    }

    public void setDecryptDepth(Integer decryptDepth) {
        this.decryptDepth = decryptDepth;
    }
}