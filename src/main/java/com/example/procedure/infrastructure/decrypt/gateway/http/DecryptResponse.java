package com.example.procedure.infrastructure.decrypt.gateway.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DecryptResponse {

    private String decryptStatus;
    private String errorMsg;
    private String integrityStatus;
    private String messageId;
    private String plainData;
    private String plainMac;
    private String ueId;

    public String getDecryptStatus() {
        return decryptStatus;
    }

    public void setDecryptStatus(String decryptStatus) {
        this.decryptStatus = decryptStatus;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getIntegrityStatus() {
        return integrityStatus;
    }

    public void setIntegrityStatus(String integrityStatus) {
        this.integrityStatus = integrityStatus;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPlainData() {
        return plainData;
    }

    public void setPlainData(String plainData) {
        this.plainData = plainData;
    }

    public String getPlainMac() {
        return plainMac;
    }

    public void setPlainMac(String plainMac) {
        this.plainMac = plainMac;
    }

    public String getUeId() {
        return ueId;
    }

    public void setUeId(String ueId) {
        this.ueId = ueId;
    }
}
