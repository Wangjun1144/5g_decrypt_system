package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;

/**
 * 单条消息在“完整处理主链”中的共享上下文。
 *
 * 设计目的：
 * 1. 统一承载一条消息处理过程中的中间状态
 * 2. 避免 MessageProcessor.process(...) 中出现大量零散的临时变量
 * 3. 为后续继续向“阶段化 / pipeline 化”演进做准备
 *
 * 当前阶段约束：
 * - 只做结构收口，不改变现有业务行为
 * - 不引入复杂模式，先把主链共享数据的语义表达清楚
 *
 * 当前上下文主要承载四类信息：
 *
 * 一、输入主体
 * - message：当前正在处理的信令消息
 *
 * 二、消息静态特征
 * - encrypted：当前消息是否被判定为加密消息
 * - encryptedType：当前消息的加密类型，例如 NAS / PDCP / NAS+PDCP / NONE
 *
 * 三、主链阶段产物
 * - category：消息分类结果
 * - ueContext：本轮处理时读取到的 UE 上下文
 * - decryptResult：本轮解密尝试结果
 * - procedureMatchResult：本轮流程识别结果
 *
 * 设计原则：
 * - 它是“主链共享上下文”，不是全局状态对象
 * - 它服务于“一条消息的一次处理过程”
 * - 它表达阶段语义，但不承载具体业务规则
 */
public class MessageProcessingContext {

    /**
     * 当前正在处理的信令消息。
     *
     * 这是整个上下文的核心输入对象，
     * 其余阶段结果都围绕它展开。
     */
    private final SignalingMessage message;

    /**
     * 当前消息是否被判定为加密。
     *
     * 这里在构造时就固化下来，
     * 目的是让当前处理轮次中的判断更稳定、更直接。
     */
    private final boolean encrypted;

    /**
     * 当前消息的加密类型。
     *
     * 典型值：
     * - NAS
     * - PDCP
     * - NAS+PDCP
     * - NONE
     *
     * 当前仍保持与原始消息一致的取值来源，
     * 不在这里强行改变底层语义。
     */
    private final String encryptedType;

    /**
     * 消息分类结果。
     *
     * 由 MessageClassificationService 负责写入。
     */
    private MessageCategory category;

    /**
     * 当前消息对应的 UE 上下文。
     *
     * 这是本轮处理时读到的上下文快照，
     * 不是系统中的唯一事实来源。
     */
    private UEContext ueContext;

    /**
     * 本轮解密尝试结果。
     *
     * 由解密阶段写入，供主链后续判断：
     * - 是否等待
     * - 是否回流
     * - 是否继续主流程
     */
    private DecryptAttemptResult decryptResult;

    /**
     * 本轮流程识别结果。
     *
     * 如果当前消息未进入流程识别阶段，这个字段通常为 null。
     */
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

    /**
     * 当前消息是否已经完成分类。
     *
     * 这个方法只表达“分类结果是否已经产生”，
     * 不承载分类规则本身。
     */
    public boolean hasCategory() {
        return category != null;
    }

    /**
     * 当前处理轮是否已经加载到 UE 上下文。
     */
    public boolean hasUeContext() {
        return ueContext != null;
    }

    /**
     * 当前处理轮是否已经产生解密结果。
     */
    public boolean hasDecryptResult() {
        return decryptResult != null;
    }

    /**
     * 当前处理轮是否已经产生流程识别结果。
     */
    public boolean hasProcedureMatchResult() {
        return procedureMatchResult != null;
    }

    /**
     * 当前轮解密是否成功。
     *
     * 这个语义方法的价值在于：
     * 后续主链代码不需要反复直接比较枚举状态。
     */
    public boolean isDecryptOk() {
        return decryptResult != null
                && decryptResult.getStatus() == DecryptAttemptResult.Status.OK;
    }

    /**
     * 当前轮解密是否进入等待状态。
     *
     * 典型场景：
     * - 缺少 NAS key
     * - 缺少 RRC key
     * - 缺少算法信息
     */
    public boolean isDecryptWaiting() {
        return decryptResult != null
                && decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING;
    }

    /**
     * 当前消息是否属于流程相关消息。
     *
     * 当前规则保持和主链现有逻辑一致：
     * - PROCEDURE_DRIVING
     * - PROCEDURE_AUX
     */
    public boolean isProcedureMessage() {
        return category == MessageCategory.PROCEDURE_DRIVING
                || category == MessageCategory.PROCEDURE_AUX;
    }

    /**
     * 当前流程识别是否成功匹配到有效流程。
     *
     * 当前系统约定：
     * - status == 0 视为匹配成功
     */
    public boolean hasMatchedProcedure() {
        return procedureMatchResult != null
                && procedureMatchResult.getStatus() == 0;
    }

    /**
     * 返回当前匹配到的 procedureId。
     *
     * 如果当前没有有效流程匹配结果，则返回 null。
     * 这样外层调用方就不必每次都手写判空逻辑。
     */
    public String getMatchedProcedureId() {
        if (!hasMatchedProcedure()) {
            return null;
        }
        return procedureMatchResult.getProcedureId();
    }

    /**
     * 返回当前匹配到的流程类型编码。
     *
     * 如果当前没有有效流程匹配，或流程类型为空，则返回 null。
     */
    public String getMatchedProcedureTypeCode() {
        if (!hasMatchedProcedure()) {
            return null;
        }

        if (procedureMatchResult.getProcedureType() == null) {
            return null;
        }

        return procedureMatchResult.getProcedureType().getCode();
    }
}
