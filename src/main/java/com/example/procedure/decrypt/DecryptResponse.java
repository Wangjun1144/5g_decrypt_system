package com.example.procedure.decrypt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecryptResponse {
    private String decryptStatus;
    private String errorMsg;
    private String integrityStatus; // 或 plaintextHex，看你服务字段名
    private String messageId;
    private String plainData;
    private String plainMac;
    private String ueId;

}
