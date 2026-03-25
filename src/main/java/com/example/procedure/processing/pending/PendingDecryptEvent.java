package com.example.procedure.processing.pending;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.decrypt.DecryptAttemptResult;

import java.util.UUID;

/**
 * pending decrypt 状态事件。
 *
 * 当前用途：
 * 1. 表达待解密消息在等待/重试过程中的状态变化
 * 2. 为单体内的 pending decrypt 生命周期提供统一观测点
 * 3. 为后续把 waiting state / retry worker 独立出去预留事件模型
 */
public class PendingDecryptEvent {

    /**
     * 事件 ID。
     */
    private final String eventId;

    /**
     * 事件类型名称。
     */
    private final String eventType;

    /**
     * 当前事件动作。
     *
     * 典型值：
     * - pending-enqueued
     * - pending-retry-batch
     * - pending-retry-ok
     * - pending-retry-waiting
     * - pending-retry-failed
     * - pending-retry-skip
     * - pending-retry-requeue
     */
    private final String action;

    /**
     * 关联 ID。
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
     * 当前等待原因。
     */
    private final DecryptAttemptResult.WaitReason waitReason;

    /**
     * 当前加密类型。
     */
    private final String encryptedType;

    /**
     * 当前错误信息。
     */
    private final String error;

    /**
     * 当前队列大小。
     */
    private final Integer queueSize;

    /**
     * 当前重试批大小。
     */
    private final Integer batchSize;

    /**
     * 事件发布时间戳。
     */
    private final long publishedAtMs;

    /**
     * 构造 pending decrypt 事件。
     *
     * @param action 当前事件动作
     * @param correlationId 关联 ID
     * @param ueId UE 标识
     * @param messageId 消息 ID
     * @param messageType 消息类型
     * @param frameNo 帧号
     * @param messageTimestamp 消息时间戳
     * @param sourceType 来源类型
     * @param sourceName 来源名称
     * @param reentry 是否回流
     * @param waitReason 当前等待原因
     * @param encryptedType 当前加密类型
     * @param error 当前错误信息
     * @param queueSize 当前队列大小
     * @param batchSize 当前批大小
     */
    public PendingDecryptEvent(
            String action,
            String correlationId,
            String ueId,
            String messageId,
            String messageType,
            Long frameNo,
            Long messageTimestamp,
            MessageSourceType sourceType,
            String sourceName,
            boolean reentry,
            DecryptAttemptResult.WaitReason waitReason,
            String encryptedType,
            String error,
            Integer queueSize,
            Integer batchSize
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "PendingDecryptEvent";
        this.action = action;
        this.correlationId = correlationId;
        this.ueId = ueId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.frameNo = frameNo;
        this.messageTimestamp = messageTimestamp;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.reentry = reentry;
        this.waitReason = waitReason;
        this.encryptedType = encryptedType;
        this.error = error;
        this.queueSize = queueSize;
        this.batchSize = batchSize;
        this.publishedAtMs = System.currentTimeMillis();
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
     * 获取动作名。
     *
     * @return 动作名
     */
    public String getAction() {
        return action;
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
     * 判断是否为回流。
     *
     * @return true 表示回流
     */
    public boolean isReentry() {
        return reentry;
    }

    /**
     * 获取等待原因。
     *
     * @return 等待原因
     */
    public DecryptAttemptResult.WaitReason getWaitReason() {
        return waitReason;
    }

    /**
     * 获取加密类型。
     *
     * @return 加密类型
     */
    public String getEncryptedType() {
        return encryptedType;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    public String getError() {
        return error;
    }

    /**
     * 获取当前队列大小。
     *
     * @return 当前队列大小
     */
    public Integer getQueueSize() {
        return queueSize;
    }

    /**
     * 获取当前批大小。
     *
     * @return 当前批大小
     */
    public Integer getBatchSize() {
        return batchSize;
    }

    /**
     * 获取发布时间戳。
     *
     * @return 发布时间戳
     */
    public long getPublishedAtMs() {
        return publishedAtMs;
    }
}
