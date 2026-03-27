package com.example.procedure.model;

import java.io.Serializable;

/**
 * UE context model shared by the context and procedure-related flows.
 *
 * It stores the current UE identity bindings, selected security algorithms,
 * and derived keys needed by later decrypt and procedure stages.
 */
public class UEContext implements Serializable {

    private String ueId;
    private String supi;
    private String amfUeNgapId;
    private String ranUeNgapId;
    private String crnti;
    private String cellId;

    private String nasCipherAlg;
    private String nasIntAlg;
    private String rrcIntAlg;
    private String rrcCipherAlg;

    private String attachState;

    private String kSeaf;
    private String kAmf;
    private String securityKeyHex;

    private String kNasEnc;
    private String kNasInt;
    private String kRrcEnc;
    private String kRrcInt;

    public String getUeId() {
        return ueId;
    }

    public void setUeId(String ueId) {
        this.ueId = ueId;
    }

    public String getSupi() {
        return supi;
    }

    public void setSupi(String supi) {
        this.supi = supi;
    }

    public String getAmfUeNgapId() {
        return amfUeNgapId;
    }

    public void setAmfUeNgapId(String amfUeNgapId) {
        this.amfUeNgapId = amfUeNgapId;
    }

    public String getRanUeNgapId() {
        return ranUeNgapId;
    }

    public void setRanUeNgapId(String ranUeNgapId) {
        this.ranUeNgapId = ranUeNgapId;
    }

    public String getCrnti() {
        return crnti;
    }

    public void setCrnti(String crnti) {
        this.crnti = crnti;
    }

    public String getCellId() {
        return cellId;
    }

    public void setCellId(String cellId) {
        this.cellId = cellId;
    }

    public String getNasCipherAlg() {
        return nasCipherAlg;
    }

    public void setNasCipherAlg(String nasCipherAlg) {
        this.nasCipherAlg = nasCipherAlg;
    }

    public String getNasIntAlg() {
        return nasIntAlg;
    }

    public void setNasIntAlg(String nasIntAlg) {
        this.nasIntAlg = nasIntAlg;
    }

    public String getRrcIntAlg() {
        return rrcIntAlg;
    }

    public void setRrcIntAlg(String rrcIntAlg) {
        this.rrcIntAlg = rrcIntAlg;
    }

    public String getRrcCipherAlg() {
        return rrcCipherAlg;
    }

    public void setRrcCipherAlg(String rrcCipherAlg) {
        this.rrcCipherAlg = rrcCipherAlg;
    }

    public String getAttachState() {
        return attachState;
    }

    public void setAttachState(String attachState) {
        this.attachState = attachState;
    }

    public String getKSeaf() {
        return kSeaf;
    }

    public void setKSeaf(String kSeaf) {
        this.kSeaf = kSeaf;
    }

    public String getKAmf() {
        return kAmf;
    }

    public void setKAmf(String kAmf) {
        this.kAmf = kAmf;
    }

    public String getSecurityKeyHex() {
        return securityKeyHex;
    }

    public void setSecurityKeyHex(String securityKeyHex) {
        this.securityKeyHex = securityKeyHex;
    }

    public String getKNasEnc() {
        return kNasEnc;
    }

    public void setKNasEnc(String kNasEnc) {
        this.kNasEnc = kNasEnc;
    }

    public String getKNasInt() {
        return kNasInt;
    }

    public void setKNasInt(String kNasInt) {
        this.kNasInt = kNasInt;
    }

    public String getKRrcEnc() {
        return kRrcEnc;
    }

    public void setKRrcEnc(String kRrcEnc) {
        this.kRrcEnc = kRrcEnc;
    }

    public String getKRrcInt() {
        return kRrcInt;
    }

    public void setKRrcInt(String kRrcInt) {
        this.kRrcInt = kRrcInt;
    }
}
