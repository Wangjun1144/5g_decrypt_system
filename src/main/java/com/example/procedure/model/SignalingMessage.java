package com.example.procedure.model;

import com.example.procedure.model.tree.MessageTree;
import com.example.procedure.parser.*;
import lombok.Data;

import java.util.List;

@Data
public class SignalingMessage {

    private String msgId;
    public String getMsgId() {
        return meta != null ? meta.getMsgId() : null;
    }

    public void setMsgId(String msgId) {
        ensureMeta();
        meta.setMsgId(msgId);
    }
    private String ueId;
    private String iface;
    public String getIface() {
        return meta != null ? meta.getIface() : null;
    }

    public void setIface(String iface) {
        ensureMeta();
        meta.setIface(iface);
    }

    private String direction;
    public String getDirection() {
        return meta != null ? meta.getDirection() : null;
    }

    public void setDirection(String direction) {
        ensureMeta();
        meta.setDirection(direction);
    }

    private String protocolLayer;
    private String msgType;
    private long timestamp;
    public Long getTimestamp() {
        return meta != null ? meta.getTimestamp() : null;
    }

    public void setTimestamp(Long timestamp) {
        ensureMeta();
        meta.setTimestamp(timestamp);
    }

    private long frameNo;
    public Long getFrameNo() {
        return meta != null ? meta.getFrameNo() : null;
    }

    public void setFrameNo(Long frameNo) {
        ensureMeta();
        meta.setFrameNo(frameNo);
    }

    private MessagePayload payload;

    private MacInfo macInfo;
    private PdcpInfo pdcpInfo;
    private RrcInfo rrcInfo;
    private List<NgapInfo> ngapInfoList;
    private NUARInfo nuarInfo;
    private List<NasInfo> nasList;

    private Boolean encrypted;
    public Boolean getEncrypted() {
        return decryptContext != null ? decryptContext.getEncrypted() : null;
    }

    public void setEncrypted(Boolean encrypted) {
        ensureDecryptContext();
        decryptContext.setEncrypted(encrypted);
    }

    private String encryptedType;
    public String getEncryptedType() {
        return decryptContext != null ? decryptContext.getEncryptedType() : null;
    }

    public void setEncryptedType(String encryptedType) {
        ensureDecryptContext();
        decryptContext.setEncryptedType(encryptedType);
    }

    private String decryptPlainHex;
    public String getDecryptPlainHex() {
        return decryptContext != null ? decryptContext.getDecryptPlainHex() : null;
    }

    public void setDecryptPlainHex(String decryptPlainHex) {
        ensureDecryptContext();
        decryptContext.setDecryptPlainHex(decryptPlainHex);
    }

    private String decryptMacHex;
    public String getDecryptMacHex() {
        return decryptContext != null ? decryptContext.getDecryptMacHex() : null;
    }

    public void setDecryptMacHex(String decryptMacHex) {
        ensureDecryptContext();
        decryptContext.setDecryptMacHex(decryptMacHex);
    }

    private boolean decrypted;

    private Integer decryptDepth;
    public Integer getDecryptDepth() {
        return decryptContext != null ? decryptContext.getDecryptDepth() : null;
    }

    public void setDecryptDepth(Integer decryptDepth) {
        ensureDecryptContext();
        decryptContext.setDecryptDepth(decryptDepth);
    }

    private String decryptPath;
    public String getDecryptPath() {
        return decryptContext != null ? decryptContext.getDecryptPath() : null;
    }

    public void setDecryptPath(String decryptPath) {
        ensureDecryptContext();
        decryptContext.setDecryptPath(decryptPath);
    }

    private String decryptTargetLayer;
    public String getDecryptTargetLayer() {
        return decryptContext != null ? decryptContext.getDecryptTargetLayer() : null;
    }

    public void setDecryptTargetLayer(String decryptTargetLayer) {
        ensureDecryptContext();
        decryptContext.setDecryptTargetLayer(decryptTargetLayer);
    }

    private Integer decryptTargetNasIndex; // 兼容旧逻辑，可逐步废弃

    /** 新增：本轮解密真正针对的原始节点 ID */
    private String decryptTargetNodeId;
    public String getDecryptTargetNodeId() {
        return decryptContext != null ? decryptContext.getDecryptTargetNodeId() : null;
    }

    public void setDecryptTargetNodeId(String decryptTargetNodeId) {
        ensureDecryptContext();
        decryptContext.setDecryptTargetNodeId(decryptTargetNodeId);
    }

    /**
     * 新增：
     * 回流消息来自哪个原始节点。
     * 它不是长期业务字段，主要用于 merge/graft。
     */
    private String reentrySourceNodeId;
    public String getReentrySourceNodeId() {
        return decryptContext != null ? decryptContext.getReentrySourceNodeId() : null;
    }

    public void setReentrySourceNodeId(String reentrySourceNodeId) {
        ensureDecryptContext();
        decryptContext.setReentrySourceNodeId(reentrySourceNodeId);
    }

    private MessageTree messageTree;

    /** 消息元信息 */
    private MessageMeta meta = new MessageMeta();

    /** 解密/回流上下文 */
    private DecryptContext decryptContext = new DecryptContext();

    public MessageMeta getMeta() {
        return meta;
    }

    public void setMeta(MessageMeta meta) {
        this.meta = (meta != null) ? meta : new MessageMeta();
    }

    public DecryptContext getDecryptContext() {
        return decryptContext;
    }

    public void setDecryptContext(DecryptContext decryptContext) {
        this.decryptContext = (decryptContext != null) ? decryptContext : new DecryptContext();
    }

    private void ensureMeta() {
        if (this.meta == null) {
            this.meta = new MessageMeta();
        }
    }

    private void ensureDecryptContext() {
        if (this.decryptContext == null) {
            this.decryptContext = new DecryptContext();
        }
    }
}