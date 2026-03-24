package com.example.procedure.processing.binding;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.application.message.SignalingMessagePipelineRequest;
import com.example.procedure.model.SignalingMessage;

/**
 * 绑定阶段请求对象。
 *
 * 当前用途：
 * 1. 统一承接单条消息进入 binding 阶段的入口参数
 * 2. 把来源元数据从 pipeline 层继续传递到 binding 层
 * 3. 为后续 binding 事件化、异步化、独立 worker 化预埋稳定输入模型
 */
public class BindingProcessRequest {

    /**
     * 当前要进入绑定阶段的信令消息。
     */
    private final SignalingMessage message;

    /**
     * 当前消息来源类型。
     */
    private final MessageSourceType sourceType;

    /**
     * 当前消息来源名称。
     */
    private final String sourceName;

    /**
     * 当前链路相关联的关联 ID。
     */
    private final String correlationId;

    /**
     * 当前消息是否属于内部回流/重入。
     */
    private final boolean reentry;

    /**
     * 构造绑定阶段请求对象。
     *
     * @param message       当前信令消息
     * @param sourceType    来源类型
     * @param sourceName    来源名称
     * @param correlationId 关联 ID
     * @param reentry       是否回流
     */
    public BindingProcessRequest(
            SignalingMessage message,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.correlationId = correlationId;
        this.reentry = reentry;
    }

    /**
     * 从 pipeline 请求对象构造 binding 请求对象。
     *
     * 这样做的意义是：
     * - binding 层不需要依赖 application 层实现细节
     * - 但可以复用同一份来源元数据
     *
     * @param request pipeline 层请求对象
     * @return binding 阶段请求对象
     */
    public static BindingProcessRequest fromPipelineRequest(SignalingMessagePipelineRequest request) {
        return new BindingProcessRequest(
                request.getMessage(),
                request.getSourceType(),
                request.getSourceName(),
                request.getCorrelationId(),
                request.isReentry()
        );
    }

    /**
     * 获取当前消息。
     *
     * @return 当前进入 binding 的消息
     */
    public SignalingMessage getMessage() {
        return message;
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
     * 判断当前请求是否来自回流。
     *
     * @return true 表示回流，false 表示普通入口
     */
    public boolean isReentry() {
        return reentry;
    }
}
