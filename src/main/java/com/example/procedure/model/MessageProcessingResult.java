package com.example.procedure.model;


import lombok.Data;
/**
 * DEMO 版的消息处理结果（可变 DTO）
 */
@Data
public class MessageProcessingResult {

    private String ueId;
    private String msgType;
    private MessageCategory category;
    private String procedureId;    // 可能为 null
    private String procedureType;  // 可能为 null

    public MessageProcessingResult(String ueId,
                                   String msgType,
                                   MessageCategory category,
                                   String procedureId,
                                   String procedureType) {
        this.ueId = ueId;
        this.msgType = msgType;
        this.category = category;
        this.procedureId = procedureId;
        this.procedureType = procedureType;
    }
}

