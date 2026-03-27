package com.example.procedure.processing.pending.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PendingMessageRepository 的内存实现。
 *
 * 设计目标：
 * 1. 保持当前单体原型的行为不变
 * 2. 把原先 PendingMessageService 中的队列状态迁移到 repository
 * 3. 为后续切 Redis / MQ 预留边界
 *
 * 注意：
 * - 当前仍然是进程内内存队列
 * - TTL / 队列长度策略保持原逻辑
 */
@Repository
public class InMemoryPendingMessageRepository implements PendingMessageRepository {
    // REFACTOR STEP: PENDING_SUBPACKAGE_REORG

    private static final Logger log = LoggerFactory.getLogger(InMemoryPendingMessageRepository.class);

    /** 每个 UE 最多保留多少条等待消息 */
    private static final int MAX_PER_UE = 2000;

    /** 过期时间：24 小时 */
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    /**
     * key = ueId
     * value = 该 UE 下的等待消息队列
     */
    private final Map<String, Deque<PendingMessageRecord>> pendingMap = new ConcurrentHashMap<>();

    @Override
    public void enqueue(String ueId, PendingMessageRecord record) {
        String normalizedUeId = normalizeUeId(ueId);

        Deque<PendingMessageRecord> queue =
                pendingMap.computeIfAbsent(normalizedUeId, k -> new ArrayDeque<>());

        cleanupExpired(queue);

        while (queue.size() >= MAX_PER_UE) {
            PendingMessageRecord dropped = queue.pollFirst();
            if (dropped != null) {
                log.warn("Pending queue overflow, drop oldest. ueId={}, droppedMsgId={}",
                        normalizedUeId, dropped.getMsgId());
            }
        }

        queue.addLast(record);

        log.info("Pending enqueue. ueId={}, msgId={}, reason={}, size={}",
                normalizedUeId,
                record.getMsgId(),
                record.getReason(),
                queue.size());
    }

    @Override
    public List<PendingMessageRecord> pollBatch(String ueId, int max) {
        String normalizedUeId = normalizeUeId(ueId);

        Deque<PendingMessageRecord> queue = pendingMap.get(normalizedUeId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }

        cleanupExpired(queue);

        List<PendingMessageRecord> result = new ArrayList<>(Math.min(max, queue.size()));
        for (int i = 0; i < max; i++) {
            PendingMessageRecord item = queue.pollFirst();
            if (item == null) {
                break;
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public void requeue(String ueId, PendingMessageRecord record) {
        String normalizedUeId = normalizeUeId(ueId);

        Deque<PendingMessageRecord> queue =
                pendingMap.computeIfAbsent(normalizedUeId, k -> new ArrayDeque<>());

        cleanupExpired(queue);

        while (queue.size() >= MAX_PER_UE) {
            PendingMessageRecord dropped = queue.pollFirst();
            if (dropped != null) {
                log.warn("Pending queue overflow(requeue), drop oldest. ueId={}, droppedMsgId={}",
                        normalizedUeId, dropped.getMsgId());
            }
        }

        queue.addLast(record);
    }

    @Override
    public int size(String ueId) {
        String normalizedUeId = normalizeUeId(ueId);
        Deque<PendingMessageRecord> queue = pendingMap.get(normalizedUeId);
        return queue == null ? 0 : queue.size();
    }

    /**
     * 清理队首已经过期的 waiting 记录。
     *
     * 说明：
     * 当前先保持旧逻辑，只清理队首连续过期项，
     * 不做整队扫描，以减少额外开销。
     */
    private void cleanupExpired(Deque<PendingMessageRecord> queue) {
        long now = System.currentTimeMillis();

        while (!queue.isEmpty()) {
            PendingMessageRecord first = queue.peekFirst();
            if (first == null) {
                break;
            }

            if (now - first.getEnqueueAt() > TTL_MS) {
                PendingMessageRecord removed = queue.pollFirst();
                if (removed != null) {
                    log.warn("Pending expired drop. msgId={}, reason={}",
                            removed.getMsgId(), removed.getReason());
                }
            } else {
                break;
            }
        }
    }

    private String normalizeUeId(String ueId) {
        return (ueId == null || ueId.isEmpty()) ? "UNKNOWN_UE" : ueId;
    }
}
