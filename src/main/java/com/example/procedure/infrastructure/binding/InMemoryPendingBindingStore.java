package com.example.procedure.infrastructure.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.resolve.PendingBindingStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory implementation for pending binding buffers and wait queues.
 */
@Component
public class InMemoryPendingBindingStore implements PendingBindingStore {

    private static final Duration PENDING_TTL = Duration.ofSeconds(120);

    private final Map<String, List<PendingMsg>> pendingByNgapId = new ConcurrentHashMap<>();
    private final Map<String, List<PendingMsg>> pendingByRntiType = new ConcurrentHashMap<>();
    private final Deque<String> unboundNgapIds = new ConcurrentLinkedDeque<>();
    private final Deque<String> unboundRntiTypes = new ConcurrentLinkedDeque<>();
    private final Set<String> queuedNgapIds = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedRntiTypes = ConcurrentHashMap.newKeySet();
    private final Deque<String> ueWaitNgap = new ConcurrentLinkedDeque<>();
    private final Deque<String> ueWaitRntiType = new ConcurrentLinkedDeque<>();
    private final Set<String> queuedUeWaitNgap = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedUeWaitRnti = ConcurrentHashMap.newKeySet();

    @Override
    public void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long expireBefore = now - PENDING_TTL.toMillis();

        cleanupPendingMap(pendingByNgapId, expireBefore);
        cleanupPendingMap(pendingByRntiType, expireBefore);
    }

    @Override
    public BufferDecision buffer(SignalingMessage msg, String ngapId, String rntiType) {
        long now = System.currentTimeMillis();

        if (!isEmpty(ngapId)) {
            bufferByNgap(msg, ngapId, now);
            return BufferDecision.bufferedByNgap(ngapId);
        }

        if (!isEmpty(rntiType)) {
            bufferByRnti(msg, rntiType, now);
            return BufferDecision.bufferedByRnti(rntiType);
        }

        return BufferDecision.notBufferable();
    }

    @Override
    public void ensureUeInWaitQueuesIfNeeded(
            String ueId,
            boolean ueNgapUnbound,
            boolean ueRntiUnbound
    ) {
        if (isEmpty(ueId)) {
            return;
        }

        if (ueNgapUnbound) {
            enqueueUeWaitNgapOnce(ueId);
        }

        if (ueRntiUnbound) {
            enqueueUeWaitRntiOnce(ueId);
        }
    }

    @Override
    public String peekFirstWaitingUeForNgap() {
        return ueWaitNgap.peekFirst();
    }

    @Override
    public String pollFirstWaitingUeForNgap() {
        return pollUeWaitNgap();
    }

    @Override
    public String peekFirstWaitingUeForRnti() {
        return ueWaitRntiType.peekFirst();
    }

    @Override
    public String pollFirstWaitingUeForRnti() {
        return pollUeWaitRnti();
    }

    @Override
    public void removeUeWaitNgap(String ueId) {
        queuedUeWaitNgap.remove(ueId);
    }

    @Override
    public void removeUeWaitRnti(String ueId) {
        queuedUeWaitRnti.remove(ueId);
    }

    @Override
    public String pollFirstQueuedNgapCandidate() {
        return unboundNgapIds.pollFirst();
    }

    @Override
    public void markNgapDequeued(String ngapId) {
        queuedNgapIds.remove(ngapId);
    }

    @Override
    public String pollFirstQueuedRntiCandidate() {
        return unboundRntiTypes.pollFirst();
    }

    @Override
    public void markRntiDequeued(String rntiType) {
        queuedRntiTypes.remove(rntiType);
    }

    @Override
    public List<SignalingMessage> releaseNgapPending(String ngapId, String ueId) {
        return releasePendingByKey(ngapId, ueId, pendingByNgapId, queuedNgapIds);
    }

    @Override
    public List<SignalingMessage> releaseRntiPending(String rntiType, String ueId) {
        return releasePendingByKey(rntiType, ueId, pendingByRntiType, queuedRntiTypes);
    }

    private void bufferByNgap(SignalingMessage msg, String ngapId, long now) {
        appendPendingMessage(pendingByNgapId, ngapId, msg, now);
        enqueueNgapCandidateOnce(ngapId);
    }

    private void bufferByRnti(SignalingMessage msg, String rntiType, long now) {
        appendPendingMessage(pendingByRntiType, rntiType, msg, now);
        enqueueRntiCandidateOnce(rntiType);
    }

    private void appendPendingMessage(
            Map<String, List<PendingMsg>> pendingMap,
            String key,
            SignalingMessage msg,
            long now
    ) {
        pendingMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new PendingMsg(msg, now));
    }

    private void enqueueNgapCandidateOnce(String ngapId) {
        if (!isEmpty(ngapId) && queuedNgapIds.add(ngapId)) {
            unboundNgapIds.offerLast(ngapId);
        }
    }

    private void enqueueRntiCandidateOnce(String rntiType) {
        if (!isEmpty(rntiType) && queuedRntiTypes.add(rntiType)) {
            unboundRntiTypes.offerLast(rntiType);
        }
    }

    private void enqueueUeWaitNgapOnce(String ueId) {
        if (queuedUeWaitNgap.add(ueId)) {
            ueWaitNgap.offerLast(ueId);
        }
    }

    private void enqueueUeWaitRntiOnce(String ueId) {
        if (queuedUeWaitRnti.add(ueId)) {
            ueWaitRntiType.offerLast(ueId);
        }
    }

    private String pollUeWaitNgap() {
        String ueId = ueWaitNgap.pollFirst();
        if (ueId != null) {
            queuedUeWaitNgap.remove(ueId);
        }
        return ueId;
    }

    private String pollUeWaitRnti() {
        String ueId = ueWaitRntiType.pollFirst();
        if (ueId != null) {
            queuedUeWaitRnti.remove(ueId);
        }
        return ueId;
    }

    private List<SignalingMessage> releasePendingByKey(
            String key,
            String ueId,
            Map<String, List<PendingMsg>> pendingMap,
            Set<String> queuedKeys
    ) {
        List<PendingMsg> list = pendingMap.remove(key);
        if (list == null || list.isEmpty()) {
            queuedKeys.remove(key);
            return List.of();
        }

        list.sort(Comparator.comparingLong(p -> p.ts));

        List<SignalingMessage> result = new ArrayList<>(list.size());
        for (PendingMsg pendingMsg : list) {
            pendingMsg.msg.setUeId(ueId);
            result.add(pendingMsg.msg);
        }

        queuedKeys.remove(key);
        return result;
    }

    private void cleanupPendingMap(Map<String, List<PendingMsg>> map, long expireBefore) {
        for (Iterator<Map.Entry<String, List<PendingMsg>>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<PendingMsg>> entry = it.next();
            List<PendingMsg> list = entry.getValue();

            if (list == null || list.isEmpty()) {
                it.remove();
                continue;
            }

            synchronized (list) {
                list.removeIf(p -> p.ts < expireBefore);
            }

            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static class PendingMsg {

        final SignalingMessage msg;
        final long ts;

        PendingMsg(SignalingMessage msg, long ts) {
            this.msg = msg;
            this.ts = ts;
        }
    }
}
