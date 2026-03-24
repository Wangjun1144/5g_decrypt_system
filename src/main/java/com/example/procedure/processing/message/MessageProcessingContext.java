package com.example.procedure.processing.message;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;

/**
 * 单条消息在“完整处理主链”中的共享上下文。
 *
 * 当前设计：
 * - 正式由 MessageProcessRequest 构造
 * - 兼容旧调用时，仍允许直接传入 SignalingMessage
 * - 统一承接消息本体、来源元数据和处理阶段产物
 */
public class MessageProcessingContext {

    private final SignalingMessage message;
    private final boolean encrypted;
    private final String encryptedType;

    private final MessageSourceType sourceType;
    private final String sourceName;
    private final String correlationId;
    private final boolean reentry;

    private MessageCategory category;
    private UEContext ueContext;
    private DecryptAttemptResult decryptResult;
    private ProcedureMatchResult procedureMatchResult;

    public MessageProcessingContext(MessageProcessRequest request) {
        this.message = request.getMessage();
        this.encrypted = Boolean.TRUE.equals(message.getEncrypted());
        this.encryptedType = message.getEncryptedType();
        this.sourceType = request.getSourceType();
        this.sourceName = request.getSourceName();
        this.correlationId = request.getCorrelationId();
        this.reentry = request.isReentry();
    }

    public MessageProcessingContext(SignalingMessage message) {
        this(MessageProcessRequest.of(message));
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

    public MessageSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isReentry() {
        return reentry;
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

    public boolean hasCategory() {
        return category != null;
    }

    public boolean hasUeContext() {
        return ueContext != null;
    }

    public boolean hasDecryptResult() {
        return decryptResult != null;
    }

    public boolean hasProcedureMatchResult() {
        return procedureMatchResult != null;
    }

    public boolean isDecryptOk() {
        return decryptResult != null
                && decryptResult.getStatus() == DecryptAttemptResult.Status.OK;
    }

    public boolean isDecryptWaiting() {
        return decryptResult != null
                && decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING;
    }

    public boolean isProcedureMessage() {
        return category == MessageCategory.PROCEDURE_DRIVING
                || category == MessageCategory.PROCEDURE_AUX;
    }

    public boolean hasMatchedProcedure() {
        return procedureMatchResult != null
                && procedureMatchResult.getStatus() == 0;
    }

    public String getMatchedProcedureId() {
        if (!hasMatchedProcedure()) {
            return null;
        }
        return procedureMatchResult.getProcedureId();
    }

    public String getMatchedProcedureTypeCode() {
        if (!hasMatchedProcedure()) {
            return null;
        }

        if (procedureMatchResult.getProcedureType() == null) {
            return null;
        }

        return procedureMatchResult.getProcedureType().getCode();
    }

    public MessageStageEvent toStageEvent(String stageName) {
        return new MessageStageEvent(
                stageName,
                correlationId,
                message.getUeId(),
                getMatchedProcedureId(),
                getMatchedProcedureTypeCode(),
                message.getMsgId(),
                message.getMsgType(),
                message.getFrameNo(),
                message.getTimestamp(),
                category,
                sourceType,
                sourceName,
                reentry,
                encrypted,
                encryptedType
        );
    }
}
