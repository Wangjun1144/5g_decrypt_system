package com.example.procedure.processing.pending;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 待解密消息队列的默认实现。
 *
 * 当前阶段定位：
 * - 它是新的 pending 解密队列正式实现
 * - 对外暴露正式等待状态模型 PendingDecryptItem
 * - 内部继续复用 PendingMessageRepository
 */
@Service
public class DefaultPendingDecryptQueue implements PendingDecryptQueue {

    private final PendingMessageRepository repository;

    public DefaultPendingDecryptQueue(PendingMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(PendingDecryptItem item) {
        repository.enqueue(item.getUeId(), item.toRecord());
    }

    @Override
    public List<PendingDecryptItem> pollBatch(String ueId, int max) {
        return repository.pollBatch(ueId, max)
                .stream()
                .map(record -> PendingDecryptItem.fromRecord(ueId, record))
                .collect(Collectors.toList());
    }

    @Override
    public void requeue(PendingDecryptItem item) {
        repository.requeue(item.getUeId(), item.toRecord());
    }

    @Override
    public int size(String ueId) {
        return repository.size(ueId);
    }
}
