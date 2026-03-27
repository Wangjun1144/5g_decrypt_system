package com.example.procedure.model.message.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed PDCP-layer information extracted from one message chain.
 */
public class PdcpInfo {

    private int sequence;
    private String nodeId;
    private String sourceNodeId;
    private boolean pdcpencrypted;
    private String signallingDataHex;
    private String decyptedTexHex;
    private String originalSignallingDataHex;
    private String direction;
    private String seqnum;
    private String macHex;
    private String bearerType;
    private String bearerName;
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public Integer getSeqNumInt() {
        if (seqnum == null) return null;
        String s = seqnum.trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public boolean isPdcpencrypted() {
        return pdcpencrypted;
    }

    public void setPdcpencrypted(boolean pdcpencrypted) {
        this.pdcpencrypted = pdcpencrypted;
    }

    public String getSignallingDataHex() {
        return signallingDataHex;
    }

    public void setSignallingDataHex(String signallingDataHex) {
        this.signallingDataHex = signallingDataHex;
    }

    public String getDecyptedTexHex() {
        return decyptedTexHex;
    }

    public void setDecyptedTexHex(String decyptedTexHex) {
        this.decyptedTexHex = decyptedTexHex;
    }

    public String getOriginalSignallingDataHex() {
        return originalSignallingDataHex;
    }

    public void setOriginalSignallingDataHex(String originalSignallingDataHex) {
        this.originalSignallingDataHex = originalSignallingDataHex;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getSeqnum() {
        return seqnum;
    }

    public void setSeqnum(String seqnum) {
        this.seqnum = seqnum;
    }

    public String getMacHex() {
        return macHex;
    }

    public void setMacHex(String macHex) {
        this.macHex = macHex;
    }

    public String getBearerType() {
        return bearerType;
    }

    public void setBearerType(String bearerType) {
        this.bearerType = bearerType;
    }

    public String getBearerName() {
        return bearerName;
    }

    public void setBearerName(String bearerName) {
        this.bearerName = bearerName;
    }

    public Map<String, String> getFieldPaths() {
        return fieldPaths;
    }

    public void setFieldPaths(Map<String, String> fieldPaths) {
        this.fieldPaths = fieldPaths == null ? new LinkedHashMap<>() : fieldPaths;
    }

    public void putFieldPath(String fieldKey, String path) {
        this.fieldPaths.put(fieldKey, path);
    }

    public String mapPdcpBearerType(String value) {
        if (value == null) return "UNKNOWN";
        return switch (value.trim()) {
            case "0" -> "CCCH";
            case "1" -> "DCCH";
            default -> "UNKNOWN";
        };
    }
}
