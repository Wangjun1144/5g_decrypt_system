package com.example.procedure.application.message;

import com.example.procedure.model.SignalingMessage;

import java.util.UUID;

/**
 * 单条信令消息进入应用主链的请求对象。
 *
 * 当前用途：
 * - 统一承接单条消息进入 SignalingMessagePipeline 的入口参数
 * - 为来源、追踪标识、是否回流等元数据提供稳定承载模型
 */
public class SignalingMessagePipelineRequest {

    private final SignalingMessage message;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final String correlationId;
    private final boolean reentry;

    public SignalingMessagePipelineRequest(
            SignalingMessage message,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.sourceType = sourceType == null ? MessageSourceType.DIRECT : sourceType;
        this.sourceName = sourceName;
        this.correlationId = normalizeCorrelationId(correlationId);
        this.reentry = reentry;
    }

    public static SignalingMessagePipelineRequest of(SignalingMessage message) {
        return new SignalingMessagePipelineRequest(
                message,
                MessageSourceType.DIRECT,
                null,
                null,
                false
        );
    }

    public static SignalingMessagePipelineRequest fromPcap(
            SignalingMessage message,
            String sourceName,
            String correlationId
    ) {
        return new SignalingMessagePipelineRequest(
                message,
                MessageSourceType.PCAP,
                sourceName,
                correlationId,
                false
        );
    }

    public static SignalingMessagePipelineRequest reentry(
            SignalingMessage message,
            String sourceName,
            String correlationId
    ) {
        return new SignalingMessagePipelineRequest(
                message,
                MessageSourceType.REENTRY,
                sourceName,
                correlationId,
                true
        );
    }

    public SignalingMessage getMessage() {
        return message;
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

    private static String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }
}
