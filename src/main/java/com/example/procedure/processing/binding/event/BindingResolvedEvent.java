package com.example.procedure.processing.binding.event;

import com.example.procedure.model.message.MessageSourceType;

import java.util.UUID;

/**
 * 绑定阶段事件。
 *
 * 当前用途：
 * 1. 为 binding 阶段提供统一观测与事件发布边界
 * 2. 不改变当前单体同步执行行为
 * 3. 为后续绑定服务拆分、异步消费、消息总线对接提供稳定事件模型
 */
public class BindingResolvedEvent {

    /**
     * 事件 ID。
     */
    private final String eventId;

    /**
     * 事件类型名称。
     */
    private final String eventType;

    /**
     * 当前消息关联 ID。
     */
    private final String correlationId;

    /**
     * UE 标识。
     */
    private final String ueId;

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
     * 当前是否被缓冲。
     */
    private final boolean buffered;

    /**
     * 当前 ready 消息数。
     */
    private final int readyCount;

    /**
     * 当前释放的 pending 消息数。
     */
    private final int releasedCount;

    /**
     * 构造绑定阶段事件。
     *
     * @param correlationId 当前关联 ID
     * @param ueId 当前 UE ID
     * @param messageId 当前消息 ID
     * @param messageType 当前消息类型
     * @param frameNo 当前帧号
     * @param sourceType 来源类型
     * @param sourceName 来源名称
     * @param reentry 是否回流
     * @param buffered 是否缓冲
     * @param readyCount ready 消息数量
     * @param releasedCount released 消息数量
     */
    public BindingResolvedEvent(
            String correlationId,
            String ueId,
            String messageId,
            String messageType,
            Long frameNo,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            boolean buffered,
            int readyCount,
            int releasedCount
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "BindingResolvedEvent";
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.buffered = buffered;
        this.readyCount = readyCount;
        this.releasedCount = releasedCount;
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
     * 获取 UE ID。
     *
     * @return UE ID
     */
    public String getUeId() {
        return ueId;
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
     * 判断是否回流。
     *
     * @return true 表示回流
     */
    public boolean isReentry() {
        return reentry;
    }

    /**
     * 判断当前是否被缓冲。
     *
     * @return true 表示当前消息本轮未进入下游
     */
    public boolean isBuffered() {
        return buffered;
    }

    /**
     * 获取 ready 消息数量。
     *
     * @return ready 数量
     */
    public int getReadyCount() {
        return readyCount;
    }

    /**
     * 获取 released 消息数量。
     *
     * @return released 数量
     */
    public int getReleasedCount() {
        return releasedCount;
    }
}
