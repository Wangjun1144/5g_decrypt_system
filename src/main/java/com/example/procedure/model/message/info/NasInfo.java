package com.example.procedure.model.message.info;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed NAS-layer information extracted from one message chain.
 */
public class NasInfo {

    private int sequence;
    private String nodeId;
    private String sourceNodeId;
    private JsonNode nasNode;
    private String fullNasPduHex;
    private String cipherTextHex;
    private String decryptedTexHex;
    private String originalFullNasPduHex;
    private String originalCipherTextHex;
    private boolean encrypted;
    private String epd;
    private String spareHalfOctet;
    private String securityHeaderType;
    private String msgAuthCodeHex;
    private String seqNo;
    private String mmMessageType;
    private String nas_cipheringAlgorithm;
    private String nas_integrityProtAlgorithm;
    private String guamiMcc;
    private String guamiMnc;
    private String tmsi;
    private String regType5gs;
    private Map<String, String> fieldPaths = new LinkedHashMap<>();

    public Integer getSeqNoInt() {
        if (seqNo == null) return null;
        String s = seqNo.trim();
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

    public JsonNode getNasNode() {
        return nasNode;
    }

    public void setNasNode(JsonNode nasNode) {
        this.nasNode = nasNode;
    }

    public String getFullNasPduHex() {
        return fullNasPduHex;
    }

    public void setFullNasPduHex(String fullNasPduHex) {
        this.fullNasPduHex = fullNasPduHex;
    }

    public String getCipherTextHex() {
        return cipherTextHex;
    }

    public void setCipherTextHex(String cipherTextHex) {
        this.cipherTextHex = cipherTextHex;
    }

    public String getDecryptedTexHex() {
        return decryptedTexHex;
    }

    public void setDecryptedTexHex(String decryptedTexHex) {
        this.decryptedTexHex = decryptedTexHex;
    }

    public String getOriginalFullNasPduHex() {
        return originalFullNasPduHex;
    }

    public void setOriginalFullNasPduHex(String originalFullNasPduHex) {
        this.originalFullNasPduHex = originalFullNasPduHex;
    }

    public String getOriginalCipherTextHex() {
        return originalCipherTextHex;
    }

    public void setOriginalCipherTextHex(String originalCipherTextHex) {
        this.originalCipherTextHex = originalCipherTextHex;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getEpd() {
        return epd;
    }

    public void setEpd(String epd) {
        this.epd = epd;
    }

    public String getSpareHalfOctet() {
        return spareHalfOctet;
    }

    public void setSpareHalfOctet(String spareHalfOctet) {
        this.spareHalfOctet = spareHalfOctet;
    }

    public String getSecurityHeaderType() {
        return securityHeaderType;
    }

    public void setSecurityHeaderType(String securityHeaderType) {
        this.securityHeaderType = securityHeaderType;
    }

    public String getMsgAuthCodeHex() {
        return msgAuthCodeHex;
    }

    public void setMsgAuthCodeHex(String msgAuthCodeHex) {
        this.msgAuthCodeHex = msgAuthCodeHex;
    }

    public String getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(String seqNo) {
        this.seqNo = seqNo;
    }

    public String getMmMessageType() {
        return mmMessageType;
    }

    public void setMmMessageType(String mmMessageType) {
        this.mmMessageType = mmMessageType;
    }

    public String getNas_cipheringAlgorithm() {
        return nas_cipheringAlgorithm;
    }

    public void setNas_cipheringAlgorithm(String nas_cipheringAlgorithm) {
        this.nas_cipheringAlgorithm = nas_cipheringAlgorithm;
    }

    public String getNas_integrityProtAlgorithm() {
        return nas_integrityProtAlgorithm;
    }

    public void setNas_integrityProtAlgorithm(String nas_integrityProtAlgorithm) {
        this.nas_integrityProtAlgorithm = nas_integrityProtAlgorithm;
    }

    public String getGuamiMcc() {
        return guamiMcc;
    }

    public void setGuamiMcc(String guamiMcc) {
        this.guamiMcc = guamiMcc;
    }

    public String getGuamiMnc() {
        return guamiMnc;
    }

    public void setGuamiMnc(String guamiMnc) {
        this.guamiMnc = guamiMnc;
    }

    public String getTmsi() {
        return tmsi;
    }

    public void setTmsi(String tmsi) {
        this.tmsi = tmsi;
    }

    public String getRegType5gs() {
        return regType5gs;
    }

    public void setRegType5gs(String regType5gs) {
        this.regType5gs = regType5gs;
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
