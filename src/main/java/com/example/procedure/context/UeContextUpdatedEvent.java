package com.example.procedure.context;

import com.example.procedure.application.message.MessageSourceType;

import java.util.UUID;

/**
 * UEContext 更新事件。
 *
 * 当前用途：
 * 1. 为上下文更新链提供正式事件边界
 * 2. 让单体内可以统一观测上下文何时被创建、更新、跳过
 * 3. 为未来上下文服务独立、事件回放、outbox/Kafka 提供稳定事件模型
 */
public class UeContextUpdatedEvent {

    /**
     * 事件 ID。
     */
    private final String eventId;

    /**
     * 事件类型。
     */
    private final String eventType;

    /**
     * 当前动作。
     */
    private final String action;

    /**
     * 关联 ID。
     */
    private final String correlationId;

    /**
     * UE ID。
     */
    private final String ueId;

    /**
     * 流程 ID。
     */
    private final String procedureId;

    /**
     * 消息 ID。
     */
    private final String messageId;

    /**
     * 消息类型。
     */
    private final String messageType;

    /**
     * 帧号。
     */
    private final Long frameNo;

    /**
     * 消息时间戳。
     */
    private final Long messageTimestamp;

    /**
     * 来源类型。
     */
    private final MessageSourceType sourceType;

    /**
     * 来源名称。
     */
    private final String sourceName;

    /**
     * 是否回流。
     */
    private final boolean reentry;

    /**
     * 是否创建了新上下文。
     */
    private final boolean created;

    /**
     * 是否执行了更新。
     */
    private final boolean updated;

    /**
     * 结果说明。
     */
    private final String message;

    /**
     * 发布时间戳。
     */
    private final long publishedAtMs;

    /**
     * 构造 UEContext 更新事件。
     *
     * @param action 当前动作
     * @param correlationId 关联 ID
     * @param ueId UE ID
     * @param procedureId 流程 ID
     * @param messageId 消息 ID
     * @param messageType 消息类型
     * @param frameNo 帧号
     * @param messageTimestamp 消息时间戳
     * @param sourceType 来源类型
     * @param sourceName 来源名称
     * @param reentry 是否回流
     * @param created 是否新建
     * @param updated 是否已更新
     * @param message 结果说明
     */
    public UeContextUpdatedEvent(
            String action,
            String correlationId,
            String ueId,
            String procedureId,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            boolean created,
            boolean updated,
            String message
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "UeContextUpdatedEvent";
        this.action = action;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.procedureId = procedureId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.created = created;
        this.updated = updated;
        this.message = message;
        this.publishedAtMs = System.currentTimeMillis();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAction() {
        return action;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getUeId() {
        return ueId;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public Long getFrameNo() {
        return frameNo;
    }

    public Long getMessageTimestamp() {
        return messageTimestamp;
    }

    public MessageSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isReentry() {
        return reentry;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean isUpdated() {
        return updated;
    }

    public String getMessage() {
        return message;
    }

    public long getPublishedAtMs() {
        return publishedAtMs;
    }
}
