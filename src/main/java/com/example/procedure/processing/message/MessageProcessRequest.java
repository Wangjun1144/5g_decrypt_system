package com.example.procedure.processing.message;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.application.message.SignalingMessagePipelineRequest;
import com.example.procedure.model.SignalingMessage;

import java.util.UUID;

/**
 * 单条消息进入 MessageProcessor 主处理链的请求对象。
 *
 * 当前用途：
 * - 统一承接消息主处理链的正式入口参数
 * - 承接从应用入口透传下来的来源和追踪元数据
 */
public class MessageProcessRequest {

    private final SignalingMessage message;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final String correlationId;
    private final boolean reentry;

    public MessageProcessRequest(
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

    public static MessageProcessRequest of(SignalingMessage message) {
        return new MessageProcessRequest(
                message,
                MessageSourceType.DIRECT,
                null,
                null,
                false
        );
    }

    public static MessageProcessRequest fromPipelineRequest(SignalingMessagePipelineRequest request) {
        return new MessageProcessRequest(
                request.getMessage(),
                request.getSourceType(),
                request.getSourceName(),
                request.getCorrelationId(),
                request.isReentry()
        );
    }

    public static MessageProcessRequest reentry(
            SignalingMessage message,
            String sourceName,
            String correlationId
    ) {
        return new MessageProcessRequest(
                message,
                MessageSourceType.REENTRY,
                sourceName,
                correlationId,
                true
        );
    }

    public static MessageProcessRequest reentry(SignalingMessage message) {
        String msgId = message == null ? "unknown" : safe(message.getMsgId());
        return reentry(message, "internal-reentry:" + msgId, "reentry-" + UUID.randomUUID());
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

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}
