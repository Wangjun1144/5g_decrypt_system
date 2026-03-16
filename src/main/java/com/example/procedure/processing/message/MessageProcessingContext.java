package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;

/**
 * 消息处理上下文。
 *
 * 设计目的：
 * 1. 统一承载一次消息处理过程中的中间状态
 * 2. 避免 MessageProcessor.process(...) 中出现大量临时变量
 * 3. 为后续“主链路阶段化 / pipeline 化”做准备
 *
 * 阶段 1 约束：
 * - 只做结构收口，不改变现有业务行为
 * - 不引入复杂模式，先用简单上下文对象把过程状态串起来
 */
public class MessageProcessingContext {

    /** 当前正在处理的信令消息 */
    private final SignalingMessage message;

    /** 当前消息是否被判定为加密 */
    private final boolean encrypted;

    /** 当前消息的加密类型，例如 NAS / PDCP / NAS+PDCP / NONE */
    private final String encryptedType;

    /** 消息分类结果 */
    private MessageCategory category;

    /** 当前 UE 对应的上下文 */
    private UEContext ueContext;

    /** 本轮解密结果 */
    private DecryptAttemptResult decryptResult;

    /** 本轮流程识别结果 */
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

    public void setCategory(MessageCategory category) {
        this.category = category;
    }

    public UEContext getUeContext() {
        return ueContext;
    }

    public void setUeContext(UEContext ueContext) {
        this.ueContext = ueContext;
    }

    public DecryptAttemptResult getDecryptResult() {
        return decryptResult;
    }

    public void setDecryptResult(DecryptAttemptResult decryptResult) {
        this.decryptResult = decryptResult;
    }

    public ProcedureMatchResult getProcedureMatchResult() {
        return procedureMatchResult;
    }

    public void setProcedureMatchResult(ProcedureMatchResult procedureMatchResult) {
        this.procedureMatchResult = procedureMatchResult;
    }
}