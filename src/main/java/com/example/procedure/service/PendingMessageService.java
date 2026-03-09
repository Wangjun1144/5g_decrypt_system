package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingMessageService {

    private static final Logger log = LoggerFactory.getLogger(PendingMessageService.class);

    // 每个 UE 最多暂存多少条（先保守一些，后面可以调）
    private static final int MAX_PER_UE = 2000;
    // 暂存多久过期丢弃（毫秒）
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    private final Map<String, Deque<PendingItem>> pendingMap = new ConcurrentHashMap<>();

    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        if (ueId == null) ueId = "UNKNOWN_UE";

        Deque<PendingItem> q = pendingMap.computeIfAbsent(ueId, k -> new ArrayDeque<>());

        cleanupExpired(q);

        // 控制队列长度：超过上限丢最旧的
        while (q.size() >= MAX_PER_UE) {
            PendingItem dropped = q.pollFirst();
            if (dropped != null) {
                log.warn("Pending queue overflow, drop oldest. ueId={}, droppedMsgId={}", ueId, dropped.msgId);
            }
        }

        PendingItem item = new PendingItem(
                System.currentTimeMillis(),
                safeMsgId(msg),
                reason,
                msg
        );
        q.addLast(item);

        log.info("Enqueue pending msg. ueId={}, msgId={}, reason={}, size={}",
                ueId, item.msgId, reason, q.size());
    }

    public int size(String ueId) {
        Deque<PendingItem> q = pendingMap.get(ueId);
        return q == null ? 0 : q.size();
    }

    // 暂时不实现 drain，后面你让做再补
    // public List<PendingItem> drainReady(...) { ... }

    private void cleanupExpired(Deque<PendingItem> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty()) {
            PendingItem first = q.peekFirst();
            if (first == null) break;
            if (now - first.enqueueAt > TTL_MS) {
                PendingItem removed = q.pollFirst();
                if (removed != null) {
                    log.warn("Pending expired drop. msgId={}, reason={}", removed.msgId, removed.reason);
                }
            } else {
                break;
            }
        }
    }

    private String safeMsgId(SignalingMessage msg) {
        try {
            return msg.getMsgId();
        } catch (Exception e) {
            return "UNKNOWN_MSG";
        }
    }

    public static class PendingItem {
        public final long enqueueAt;
        public final String msgId;
        public final DecryptAttemptResult.WaitReason reason;
        public final SignalingMessage msg;

        public PendingItem(long enqueueAt, String msgId, DecryptAttemptResult.WaitReason reason, SignalingMessage msg) {
            this.enqueueAt = enqueueAt;
            this.msgId = msgId;
            this.reason = reason;
            this.msg = msg;
        }
    }

    public List<PendingItem> pollBatch(String ueId, int max) {
        Deque<PendingItem> q = pendingMap.get(ueId);
        if (q == null || q.isEmpty()) return List.of();

        cleanupExpired(q);

        List<PendingItem> out = new ArrayList<>(Math.min(max, q.size()));
        for (int i = 0; i < max; i++) {
            PendingItem it = q.pollFirst();
            if (it == null) break;
            out.add(it);
        }
        return out;
    }

    public void requeue(String ueId, PendingItem item) {
        if (ueId == null) ueId = "UNKNOWN_UE";
        Deque<PendingItem> q = pendingMap.computeIfAbsent(ueId, k -> new ArrayDeque<>());
        cleanupExpired(q);

        // 仍然要做队列上限保护
        while (q.size() >= MAX_PER_UE) {
            PendingItem dropped = q.pollFirst();
            if (dropped != null) {
                log.warn("Pending queue overflow(requeue), drop oldest. ueId={}, droppedMsgId={}", ueId, dropped.msgId);
            }
        }
        q.addLast(item);
    }
}