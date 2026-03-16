package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import lombok.Data;
import lombok.Getter;

/**
 * 消息处理上下文
 *
 * 作用：
 * 1. 统一承载一次 message processing 过程中产生的中间状态
 * 2. 避免 process(...) 方法里出现过多临时变量
 * 3. 为后续把“大方法”继续拆成 step/handler 做准备
 */
@Data
public class MessageProcessingContext {

    /** 当前正在处理的信令消息 */
    private final SignalingMessage message;

    /** 当前消息是否被判定为加密 */
    private final boolean encrypted;

    /** 加密类型：NAS / PDCP / NAS+PDCP / NONE */
    private final String encryptedType;

    /** 消息分类结果 */
    private MessageCategory category;

    /** 当前 UE 上下文 */
    private UEContext ueContext;

    /** 解密尝试结果 */
    private DecryptAttemptResult decryptResult;

    /** 流程匹配结果 */
    private ProcedureMatchResult procedureMatchResult;

    public MessageProcessingContext(SignalingMessage message) {
        this.message = message;
        this.encrypted = Boolean.TRUE.equals(message.getEncrypted());
        this.encryptedType = message.getEncryptedType();
    }

    public SignalingMessage getMessage() {
        return message;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public String getEncryptedType() {
        return encryptedType;
    }

    public MessageCategory getCategory() {
        return category;
    }

    public UEContext getUeContext() {
        return ueContext;
    }

    public DecryptAttemptResult getDecryptResult() {
        return decryptResult;
    }


    public ProcedureMatchResult getProcedureMatchResult() {
        return procedureMatchResult;
    }

}