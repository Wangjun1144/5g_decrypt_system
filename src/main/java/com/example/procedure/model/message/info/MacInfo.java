package com.example.procedure.model.message.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed MAC-layer information extracted from one message chain.
 */
public class MacInfo {

    private int sequence;
    private String nodeId;
    private String rnti;
    private String rntiType;
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

    public String getRnti() {
        return rnti;
    }

    public void setRnti(String rnti) {
        this.rnti = rnti;
    }

    public String getRntiType() {
        return rntiType;
    }

    public void setRntiType(String rntiType) {
        this.rntiType = rntiType;
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
}
