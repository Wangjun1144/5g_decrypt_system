package com.example.procedure.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class UEContext implements Serializable {

    private String ueId;           // 系统内部 UE 标识
    private String supi;           // SUPI/IMSI（这里存 imsi 纯数字串）
    private String amfUeNgapId;
    private String ranUeNgapId;
    private String crnti;
    private String cellId;

    private String nasCipherAlg;   // 原 NascipherAlg
    private String nasIntAlg;      // 原 NasIntAlg
    private String rrcIntAlg;      // 原 IntegrityProtAlgorithm
    private String rrcCipherAlg;   // 原 CipheringAlgorithm

    private String attachState;

    private String kSeaf;
    private String kAmf;
    private String securityKeyHex; // 原 SecurityKeyHex

    private String kNasEnc;   // NAS 加密密钥
    private String kNasInt;   // NAS 完保密钥

    private String kRrcEnc;
    private String kRrcInt;


}
