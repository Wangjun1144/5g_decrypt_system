package com.example.procedure.processing.message.retry;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.MessageSourceType;
import com.example.procedure.processing.pending.event.PendingDecryptEvent;
import com.example.procedure.processing.pending.event.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.queue.PendingDecryptItem;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import org.springframework.stereotype.Component;

/**
 * Publishes retry item lifecycle events from one place so executor branches can
 * stay focused on retry flow.
 */
@Component
public class PendingDecryptItemEventPublisher {

    private final PendingDecryptQueue pendingDecryptQueue;
    private final PendingDecryptEventPublisher pendingDecryptEventPublisher;
    private final PendingDecryptRetryIdentityFactory identityFactory;

    public PendingDecryptItemEventPublisher(
            PendingDecryptQueue pendingDecryptQueue,
            PendingDecryptEventPublisher pendingDecryptEventPublisher,
            PendingDecryptRetryIdentityFactory identityFactory
    ) {
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.pendingDecryptEventPublisher = pendingDecryptEventPublisher;
        this.identityFactory = identityFactory;
    }

    /**
     * Emits the success lifecycle event after decrypt succeeded and the message reentered the main chain.
     */
    public void publishRetryOk(PendingDecryptItem item, SignalingMessage message, String encType) {
        publish(PendingDecryptRetryAction.RETRY_OK, item, message, encType, null, reasonOf(item));
    }

    /**
     * Emits the waiting lifecycle event after the item was requeued with a new wait reason.
     */
    public void publishRetryWaiting(PendingDecryptItem item, SignalingMessage message, String encType) {
        publish(PendingDecryptRetryAction.RETRY_WAITING, item, message, encType, null, reasonOf(item));
    }

    /**
     * Emits the failure lifecycle event for terminal retry failures.
     */
    public void publishRetryFailed(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType,
            String error
    ) {
        publish(PendingDecryptRetryAction.RETRY_FAILED, item, message, encType, error, reasonOf(item));
    }

    /**
     * Emits the skip lifecycle event when no further waiting state is needed.
     */
    public void publishRetrySkip(PendingDecryptItem item, SignalingMessage message, String encType) {
        publish(PendingDecryptRetryAction.RETRY_SKIP, item, message, encType, null, reasonOf(item));
    }

    /**
     * Emits the requeue lifecycle event when the original item is put back untouched.
     */
    public void publishRetryRequeue(PendingDecryptItem item, SignalingMessage message, String encType) {
        publish(PendingDecryptRetryAction.RETRY_REQUEUE, item, message, encType, null, reasonOf(item));
    }

    /**
     * Builds and publishes the shared event payload used by all retry item lifecycle branches.
     */
    private void publish(
            String action,
            PendingDecryptItem item,
            SignalingMessage message,
            String encType,
            String error,
            DecryptAttemptResult.WaitReason waitReason
    ) {
        pendingDecryptEventPublisher.publish(new PendingDecryptEvent(
                action,
                identityFactory.buildRetryCorrelationId(message),
                item == null ? null : item.getUeId(),
                message == null ? null : message.getMsgId(),
                message == null ? null : message.getMsgType(),
                message == null ? null : message.getFrameNo(),
                message == null ? null : message.getTimestamp(),
                MessageSourceType.REENTRY,
                identityFactory.buildRetrySourceName(message),
                true,
                waitReason,
                encType,
                error,
                item == null ? null : pendingDecryptQueue.size(item.getUeId()),
                1
        ));
    }

    /**
     * Reads wait reason defensively so lifecycle events stay null-safe.
     */
    private DecryptAttemptResult.WaitReason reasonOf(PendingDecryptItem item) {
        return item == null ? null : item.getReason();
    }
}
