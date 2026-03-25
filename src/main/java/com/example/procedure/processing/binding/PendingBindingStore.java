package com.example.procedure.processing.binding;

import com.example.procedure.infrastructure.binding.InMemoryPendingBindingStore;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @deprecated 旧的待绑定缓冲状态存储门面。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖 processing.binding.PendingBindingStore 这个名字
 * 2. 新的正式实现已经迁到 infrastructure.binding.InMemoryPendingBindingStore
 * 3. 这里收缩为兼容壳，避免旧引用立即失效
 */
@Deprecated
@Component
public class PendingBindingStore {

    /**
     * 正式内存待绑定状态实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    private final InMemoryPendingBindingStore delegate;

    /**
     * 构造旧兼容层。
     *
     * @param delegate 正式内存待绑定状态实现
     */
    public PendingBindingStore(InMemoryPendingBindingStore delegate) {
        this.delegate = delegate;
    }

    public void cleanupExpiredPending() {
        delegate.cleanupExpiredPending();
    }

    public BufferDecision buffer(SignalingMessage msg, String ngapId, String rntiType) {
        InMemoryPendingBindingStore.BufferDecision decision = delegate.buffer(msg, ngapId, rntiType);
        if (!decision.isBuffered()) {
            return BufferDecision.notBufferable();
        }
        if (decision.getBufferedNgapId() != null) {
            return BufferDecision.bufferedByNgap(decision.getBufferedNgapId());
        }
        return BufferDecision.bufferedByRnti(decision.getBufferedRntiType());
    }

    public void ensureUeInWaitQueuesIfNeeded(
            String ueId,
            boolean ueNgapUnbound,
            boolean ueRntiUnbound
    ) {
        delegate.ensureUeInWaitQueuesIfNeeded(ueId, ueNgapUnbound, ueRntiUnbound);
    }

    public String peekFirstWaitingUeForNgap() {
        return delegate.peekFirstWaitingUeForNgap();
    }

    public String pollFirstWaitingUeForNgap() {
        return delegate.pollFirstWaitingUeForNgap();
    }

    public String peekFirstWaitingUeForRnti() {
        return delegate.peekFirstWaitingUeForRnti();
    }

    public String pollFirstWaitingUeForRnti() {
        return delegate.pollFirstWaitingUeForRnti();
    }

    public void removeUeWaitNgap(String ueId) {
        delegate.removeUeWaitNgap(ueId);
    }

    public void removeUeWaitRnti(String ueId) {
        delegate.removeUeWaitRnti(ueId);
    }

    public String pollFirstQueuedNgapCandidate() {
        return delegate.pollFirstQueuedNgapCandidate();
    }

    public void markNgapDequeued(String ngapId) {
        delegate.markNgapDequeued(ngapId);
    }

    public String pollFirstQueuedRntiCandidate() {
        return delegate.pollFirstQueuedRntiCandidate();
    }

    public void markRntiDequeued(String rntiType) {
        delegate.markRntiDequeued(rntiType);
    }

    public List<SignalingMessage> releaseNgapPending(String ngapId, String ueId) {
        return delegate.releaseNgapPending(ngapId, ueId);
    }

    public List<SignalingMessage> releaseRntiPending(String rntiType, String ueId) {
        return delegate.releaseRntiPending(rntiType, ueId);
    }

    /**
     * 旧的缓冲决策兼容对象。
     */
    public static class BufferDecision {

        private final boolean buffered;
        private final String bufferedNgapId;
        private final String bufferedRntiType;

        private BufferDecision(boolean buffered, String bufferedNgapId, String bufferedRntiType) {
            this.buffered = buffered;
            this.bufferedNgapId = bufferedNgapId;
            this.bufferedRntiType = bufferedRntiType;
        }

        public static BufferDecision bufferedByNgap(String ngapId) {
            return new BufferDecision(true, ngapId, null);
        }

        public static BufferDecision bufferedByRnti(String rntiType) {
            return new BufferDecision(true, null, rntiType);
        }

        public static BufferDecision notBufferable() {
            return new BufferDecision(false, null, null);
        }

        public boolean isBuffered() {
            return buffered;
        }

        public String getBufferedNgapId() {
            return bufferedNgapId;
        }

        public String getBufferedRntiType() {
            return bufferedRntiType;
        }
    }
}
