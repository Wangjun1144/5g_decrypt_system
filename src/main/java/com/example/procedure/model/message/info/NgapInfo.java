package com.example.procedure.model.message.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed NGAP-layer information extracted from one message chain.
 */
public class NgapInfo {

    private int sequence;
    private String nodeId;
    private String pduType;
    private String msgName;
    private String securityKeyHex;
    private String ranUeNgapId;
    private String direction;
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPduType() {
        return pduType;
    }

    public void setPduType(String pduType) {
        this.pduType = pduType;
    }

    public String getMsgName() {
        return msgName;
    }

    public void setMsgName(String msgName) {
        this.msgName = msgName;
    }

    public String getSecurityKeyHex() {
        return securityKeyHex;
    }

    public void setSecurityKeyHex(String securityKeyHex) {
        this.securityKeyHex = securityKeyHex;
    }

    public String getRanUeNgapId() {
        return ranUeNgapId;
    }

    public void setRanUeNgapId(String ranUeNgapId) {
        this.ranUeNgapId = ranUeNgapId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Map<String, String> getFieldPaths() {
        return fieldPaths;
    }

    public void setFieldPaths(Map<String, String> fieldPaths) {
        this.fieldPaths = fieldPaths == null ? new LinkedHashMap<>() : fieldPaths;
    }

    public void putFieldPath(String key, String path) {
        this.fieldPaths.put(key, path);
    }
}
