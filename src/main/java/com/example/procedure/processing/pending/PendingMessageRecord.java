package com.example.procedure.processing.pending;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;

/**
 * pending 消息记录。
 *
 * 设计意图：
 * 1. 将“等待解密消息”的存储对象从 service 中抽离
 * 2. 为后续切换 Redis / MQ / Kafka waiting topic 做统一模型准备
 * 3. 避免各处直接依赖 PendingMessageService.PendingItem 这种内部类
 *
 * 当前阶段约束：
 * - 仍然保持原有字段语义
 * - 不改变原业务行为
 */
public class PendingMessageRecord {

    /** 入队时间戳 */
    private final long enqueueAt;

    /** 消息 ID，便于日志与排查 */
    private final String msgId;

    /** 当前等待原因，例如 WAIT_NAS_KEYS / WAIT_RRC_KEYS / WAIT_ALG */
    private final DecryptAttemptResult.WaitReason reason;

    /** 原始待处理消息 */
    private final SignalingMessage message;

    public PendingMessageRecord(
            long enqueueAt,
            String msgId,
            DecryptAttemptResult.WaitReason reason,
            SignalingMessage message
    ) {
        this.enqueueAt = enqueueAt;
        this.msgId = msgId;
        this.reason = reason;
        this.message = message;
    }

    public long getEnqueueAt() {
        return enqueueAt;
    }

    public String getMsgId() {
        return msgId;
    }

    public DecryptAttemptResult.WaitReason getReason() {
        return reason;
    }

    public SignalingMessage getMessage() {
        return message;
    }
}