package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;

import java.util.List;

/**
 * Storage boundary for pending binding buffers and waiting queues.
 */
public interface PendingBindingStore {

    void cleanupExpiredPending();

    BufferDecision buffer(SignalingMessage msg, String ngapId, String rntiType);

    void ensureUeInWaitQueuesIfNeeded(String ueId, boolean ueNgapUnbound, boolean ueRntiUnbound);

    String peekFirstWaitingUeForNgap();

    String pollFirstWaitingUeForNgap();

    String peekFirstWaitingUeForRnti();

    String pollFirstWaitingUeForRnti();

    void removeUeWaitNgap(String ueId);

    void removeUeWaitRnti(String ueId);

    String pollFirstQueuedNgapCandidate();

    void markNgapDequeued(String ngapId);

    String pollFirstQueuedRntiCandidate();

    void markRntiDequeued(String rntiType);

    List<SignalingMessage> releaseNgapPending(String ngapId, String ueId);

    List<SignalingMessage> releaseRntiPending(String rntiType, String ueId);

    final class BufferDecision {

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
