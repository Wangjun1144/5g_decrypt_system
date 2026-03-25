package com.example.procedure.processing.event;

import com.example.procedure.application.message.MessageSourceType;

/**
 * 内部事件公共元数据。
 *
 * 当前用途：
 * 1. 统一承接系统内部事件的公共字段
 * 2. 让不同类型事件的发布和日志具备一致的元数据结构
 * 3. 为后续对接 outbox / MQ / Kafka 提供稳定基础契约
 */
public class InternalEventMetadata {

    /**
     * 事件 ID。
     */
    private final String eventId;

    /**
     * 事件类型。
     */
    private final String eventType;

    /**
     * 当前关联 ID。
     */
    private final String correlationId;

    /**
     * UE 标识。
     */
    private final String ueId;

    /**
     * 流程 ID。
     */
    private final String procedureId;

    /**
     * 流程类型编码。
     */
    private final String procedureTypeCode;

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
     * 是否为回流消息。
     */
    private final boolean reentry;

    /**
     * 事件发布时间戳。
     */
    private final long publishedAtMs;

    /**
     * 构造内部事件公共元数据。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param correlationId 当前关联 ID
     * @param ueId UE 标识
     * @param procedureId 流程 ID
     * @param procedureTypeCode 流程类型编码
     * @param messageId 消息 ID
     * @param messageType 消息类型
     * @param frameNo 帧号
     * @param messageTimestamp 消息时间戳
     * @param sourceType 来源类型
     * @param sourceName 来源名称
     * @param reentry 是否回流
     * @param publishedAtMs 事件发布时间戳
     */
    public InternalEventMetadata(
            String eventId,
            String eventType,
            String correlationId,
            String ueId,
            String procedureId,
            String procedureTypeCode,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            long publishedAtMs
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.procedureId = procedureId;
        this.procedureTypeCode = procedureTypeCode;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.publishedAtMs = publishedAtMs;
    }

    /**
     * 获取事件 ID。
     *
     * @return 事件 ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 获取事件类型。
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
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
     * 获取 UE 标识。
     *
     * @return UE 标识
     */
    public String getUeId() {
        return ueId;
    }

    /**
     * 获取流程 ID。
     *
     * @return 流程 ID
     */
    public String getProcedureId() {
        return procedureId;
    }

    /**
     * 获取流程类型编码。
     *
     * @return 流程类型编码
     */
    public String getProcedureTypeCode() {
        return procedureTypeCode;
    }

    /**
     * 获取消息 ID。
     *
     * @return 消息 ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 获取消息类型。
     *
     * @return 消息类型
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 获取帧号。
     *
     * @return 帧号
     */
    public Long getFrameNo() {
        return frameNo;
    }

    /**
     * 获取消息时间戳。
     *
     * @return 消息时间戳
     */
    public Long getMessageTimestamp() {
        return messageTimestamp;
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
     * 判断是否为回流消息。
     *
     * @return true 表示回流
     */
    public boolean isReentry() {
        return reentry;
    }

    /**
     * 获取事件发布时间戳。
     *
     * @return 事件发布时间戳
     */
    public long getPublishedAtMs() {
        return publishedAtMs;
    }
}
