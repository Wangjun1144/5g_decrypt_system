package com.example.procedure.processing.pending.store;

import java.util.List;

/**
 * pending 消息仓储接口。
 *
 * 文档要求将等待状态从 service 逻辑中分离出来，
 * 当前这一步先抽象接口，底层仍用内存实现。
 *
 * 未来可替换为：
 * - RedisPendingMessageRepository
 * - KafkaPendingMessageRepository
 * - MQPendingMessageRepository
 */
public interface PendingMessageRepository {

    /**
     * 入队一条待处理消息。
     */
    void enqueue(String ueId, PendingMessageRecord record);

    /**
     * 拉取一批待重试消息。
     * 语义保持与原 pollBatch 一致：取出后从队列移除。
     */
    List<PendingMessageRecord> pollBatch(String ueId, int max);

    /**
     * 重新放回队尾。
     */
    void requeue(String ueId, PendingMessageRecord record);

    /**
     * 当前 UE 的待处理数量。
     */
    int size(String ueId);
}
