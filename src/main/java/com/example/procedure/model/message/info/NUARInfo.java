package com.example.procedure.model.message.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed NAUSF authentication-response information extracted from an
 * {@code http2:json} message.
 */
public class NUARInfo {

    private int sequence;
    private String nodeId;
    private String msgName;
    private String supi;
    private String imsi;
    private String kseafHex;
    private String authResult;
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

    public String getMsgName() {
        return msgName;
    }

    public void setMsgName(String msgName) {
        this.msgName = msgName;
    }

    public String getSupi() {
        return supi;
    }

    public void setSupi(String supi) {
        this.supi = supi;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getKseafHex() {
        return kseafHex;
    }

    public void setKseafHex(String kseafHex) {
        this.kseafHex = kseafHex;
    }

    public String getAuthResult() {
        return authResult;
    }

    public void setAuthResult(String authResult) {
        this.authResult = authResult;
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
