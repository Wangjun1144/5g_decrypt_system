package com.example.procedure.model;

import com.example.procedure.model.message.DecryptContext;
import com.example.procedure.model.message.MessageMeta;
import com.example.procedure.model.message.MessagePayload;
import com.example.procedure.model.message.tree.MessageTree;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;

import java.util.List;

/**
 * 淇′护娑堟伅涓绘ā鍨嬨€? *
 * 褰撳墠闃舵鏄惧紡琛ラ綈鍏抽敭 getter/setter锛? * 閬垮厤涓诲共缂栬瘧缁х画鍙?Lombok 鐢熸垚宸紓褰卞搷銆? */
/**
 * Core signaling-message model used across the processing pipeline.
 *
 * This type brings together:
 * 1. Message identity and transport metadata.
 * 2. Parsed protocol-layer information objects.
 * 3. Decrypt and reentry state attached during processing.
 * 4. The optional tree-form representation built by streaming parsers.
 */
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
    private Integer decryptTargetNasIndex;
    private String decryptTargetNodeId;
    private String reentrySourceNodeId;

    private MessageTree messageTree;

    private MessageMeta meta = new MessageMeta();
    private DecryptContext decryptContext = new DecryptContext();

    public String getMsgId() {
        return meta != null ? meta.getMsgId() : msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
        ensureMeta();
        meta.setMsgId(msgId);
    }

    public String getUeId() {
        return ueId;
    }

    public void setUeId(String ueId) {
        this.ueId = ueId;
    }

    public String getIface() {
        return meta != null ? meta.getIface() : iface;
    }

    public void setIface(String iface) {
        this.iface = iface;
        ensureMeta();
        meta.setIface(iface);
    }

    public String getDirection() {
        return meta != null ? meta.getDirection() : direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
        ensureMeta();
        meta.setDirection(direction);
    }

    public String getProtocolLayer() {
        return protocolLayer;
    }

    public void setProtocolLayer(String protocolLayer) {
        this.protocolLayer = protocolLayer;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public Long getTimestamp() {
        return meta != null ? meta.getTimestamp() : timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp == null ? 0L : timestamp;
        ensureMeta();
        meta.setTimestamp(timestamp);
    }

    public Long getFrameNo() {
        return meta != null ? meta.getFrameNo() : frameNo;
    }

    public void setFrameNo(Long frameNo) {
        this.frameNo = frameNo == null ? 0L : frameNo;
        ensureMeta();
        meta.setFrameNo(frameNo);
    }

    public MessagePayload getPayload() {
        return payload;
    }

    public void setPayload(MessagePayload payload) {
        this.payload = payload;
    }

    public MacInfo getMacInfo() {
        return macInfo;
    }

    public void setMacInfo(MacInfo macInfo) {
        this.macInfo = macInfo;
    }

    public PdcpInfo getPdcpInfo() {
        return pdcpInfo;
    }

    public void setPdcpInfo(PdcpInfo pdcpInfo) {
        this.pdcpInfo = pdcpInfo;
    }

    public RrcInfo getRrcInfo() {
        return rrcInfo;
    }

    public void setRrcInfo(RrcInfo rrcInfo) {
        this.rrcInfo = rrcInfo;
    }

    public List<NgapInfo> getNgapInfoList() {
        return ngapInfoList;
    }

    public void setNgapInfoList(List<NgapInfo> ngapInfoList) {
        this.ngapInfoList = ngapInfoList;
    }

    public NUARInfo getNuarInfo() {
        return nuarInfo;
    }

    public void setNuarInfo(NUARInfo nuarInfo) {
        this.nuarInfo = nuarInfo;
    }

    public List<NasInfo> getNasList() {
        return nasList;
    }

    public void setNasList(List<NasInfo> nasList) {
        this.nasList = nasList;
    }

    public Boolean getEncrypted() {
        return decryptContext != null ? decryptContext.getEncrypted() : encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
        ensureDecryptContext();
        decryptContext.setEncrypted(encrypted);
    }

    public String getEncryptedType() {
        return decryptContext != null ? decryptContext.getEncryptedType() : encryptedType;
    }

    public void setEncryptedType(String encryptedType) {
        this.encryptedType = encryptedType;
        ensureDecryptContext();
        decryptContext.setEncryptedType(encryptedType);
    }

    public String getDecryptPlainHex() {
        return decryptContext != null ? decryptContext.getDecryptPlainHex() : decryptPlainHex;
    }

    public void setDecryptPlainHex(String decryptPlainHex) {
        this.decryptPlainHex = decryptPlainHex;
        ensureDecryptContext();
        decryptContext.setDecryptPlainHex(decryptPlainHex);
    }

    public String getDecryptMacHex() {
        return decryptContext != null ? decryptContext.getDecryptMacHex() : decryptMacHex;
    }

    public void setDecryptMacHex(String decryptMacHex) {
        this.decryptMacHex = decryptMacHex;
        ensureDecryptContext();
        decryptContext.setDecryptMacHex(decryptMacHex);
    }

    public boolean isDecrypted() {
        return decrypted;
    }

    public void setDecrypted(boolean decrypted) {
        this.decrypted = decrypted;
    }

    public Integer getDecryptDepth() {
        return decryptContext != null ? decryptContext.getDecryptDepth() : decryptDepth;
    }

    public void setDecryptDepth(Integer decryptDepth) {
        this.decryptDepth = decryptDepth;
        ensureDecryptContext();
        decryptContext.setDecryptDepth(decryptDepth);
    }

    public String getDecryptPath() {
        return decryptContext != null ? decryptContext.getDecryptPath() : decryptPath;
    }

    public void setDecryptPath(String decryptPath) {
        this.decryptPath = decryptPath;
        ensureDecryptContext();
        decryptContext.setDecryptPath(decryptPath);
    }

    public String getDecryptTargetLayer() {
        return decryptContext != null ? decryptContext.getDecryptTargetLayer() : decryptTargetLayer;
    }

    public void setDecryptTargetLayer(String decryptTargetLayer) {
        this.decryptTargetLayer = decryptTargetLayer;
        ensureDecryptContext();
        decryptContext.setDecryptTargetLayer(decryptTargetLayer);
    }

    public Integer getDecryptTargetNasIndex() {
        return decryptTargetNasIndex;
    }

    public void setDecryptTargetNasIndex(Integer decryptTargetNasIndex) {
        this.decryptTargetNasIndex = decryptTargetNasIndex;
    }

    public String getDecryptTargetNodeId() {
        return decryptContext != null ? decryptContext.getDecryptTargetNodeId() : decryptTargetNodeId;
    }

    public void setDecryptTargetNodeId(String decryptTargetNodeId) {
        this.decryptTargetNodeId = decryptTargetNodeId;
        ensureDecryptContext();
        decryptContext.setDecryptTargetNodeId(decryptTargetNodeId);
    }

    public String getReentrySourceNodeId() {
        return decryptContext != null ? decryptContext.getReentrySourceNodeId() : reentrySourceNodeId;
    }

    public void setReentrySourceNodeId(String reentrySourceNodeId) {
        this.reentrySourceNodeId = reentrySourceNodeId;
        ensureDecryptContext();
        decryptContext.setReentrySourceNodeId(reentrySourceNodeId);
    }

    public MessageTree getMessageTree() {
        return messageTree;
    }

    public void setMessageTree(MessageTree messageTree) {
        this.messageTree = messageTree;
    }

    public MessageMeta getMeta() {
        return meta;
    }

    public void setMeta(MessageMeta meta) {
        this.meta = meta != null ? meta : new MessageMeta();
    }

    public DecryptContext getDecryptContext() {
        return decryptContext;
    }

    public void setDecryptContext(DecryptContext decryptContext) {
        this.decryptContext = decryptContext != null ? decryptContext : new DecryptContext();
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
