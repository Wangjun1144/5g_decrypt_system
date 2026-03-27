package com.example.procedure.model.message.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed RRC-layer information extracted from one message chain.
 */
public class RrcInfo {

    private int sequence;
    private String nodeId;
    private String sourceNodeId;
    private String direction;
    private String msgName;
    private String randomValueHex;
    private String establishmentCause;
    private String crnti;
    private String integrityProtAlgorithm;
    private String cipheringAlgorithm;
    private boolean hasDedicatedNas;
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

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMsgName() {
        return msgName;
    }

    public void setMsgName(String msgName) {
        this.msgName = msgName;
    }

    public String getRandomValueHex() {
        return randomValueHex;
    }

    public void setRandomValueHex(String randomValueHex) {
        this.randomValueHex = randomValueHex;
    }

    public String getEstablishmentCause() {
        return establishmentCause;
    }

    public void setEstablishmentCause(String establishmentCause) {
        this.establishmentCause = establishmentCause;
    }

    public String getCrnti() {
        return crnti;
    }

    public void setCrnti(String crnti) {
        this.crnti = crnti;
    }

    public String getIntegrityProtAlgorithm() {
        return integrityProtAlgorithm;
    }

    public void setIntegrityProtAlgorithm(String integrityProtAlgorithm) {
        this.integrityProtAlgorithm = integrityProtAlgorithm;
    }

    public String getCipheringAlgorithm() {
        return cipheringAlgorithm;
    }

    public void setCipheringAlgorithm(String cipheringAlgorithm) {
        this.cipheringAlgorithm = cipheringAlgorithm;
    }

    public boolean isHasDedicatedNas() {
        return hasDedicatedNas;
    }

    public void setHasDedicatedNas(boolean hasDedicatedNas) {
        this.hasDedicatedNas = hasDedicatedNas;
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
