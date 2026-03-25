package com.example.procedure.context;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.model.SignalingMessage;

/**
 * UEContext 更新请求。
 *
 * 当前用途：
 * 1. 统一承接一次 UEContext 更新所需的输入
 * 2. 让上下文更新链不再只暴露裸消息 + procedureId
 * 3. 为后续上下文服务拆分、异步更新、事件回放预留稳定输入模型
 */
public class UeContextUpdateRequest {

    /**
     * 当前消息。
     */
    private final SignalingMessage message;

    /**
     * 当前关联的流程 ID。
     */
    private final String procedureId;

    /**
     * 来源类型。
     */
    private final MessageSourceType sourceType;

    /**
     * 来源名称。
     */
    private final String sourceName;

    /**
     * 关联 ID。
     */
    private final String correlationId;

    /**
     * 是否回流。
     */
    private final boolean reentry;

    /**
     * 构造 UEContext 更新请求。
     *
     * @param message 当前消息
     * @param procedureId 当前流程 ID
     * @param sourceType 来源类型
     * @param sourceName 来源名称
     * @param correlationId 关联 ID
     * @param reentry 是否回流
     */
    public UeContextUpdateRequest(
            SignalingMessage message,
            String procedureId,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.procedureId = procedureId;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.correlationId = correlationId;
        this.reentry = reentry;
    }

    /**
     * 获取当前消息。
     *
     * @return 当前消息
     */
    public SignalingMessage getMessage() {
        return message;
    }

    /**
     * 获取当前流程 ID。
     *
     * @return 当前流程 ID
     */
    public String getProcedureId() {
        return procedureId;
    }

    /**
     * 获取来源类型。
     *
     * @return 来源类型
     */
    public MessageSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 获取来源名称。
     *
     * @return 来源名称
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * 获取关联 ID。
     *
     * @return 关联 ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 判断是否回流。
     *
     * @return true 表示回流
     */
    public boolean isReentry() {
        return reentry;
    }
}
