package com.example.procedure.processing.message;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.processing.message.event.MessageObservationSnapshot;
import com.example.procedure.processing.message.event.MessageStageEventPublisher;
import com.example.procedure.processing.message.result.MessageProcessingResultAssembler;
import com.example.procedure.processing.message.result.MessageProcessingSummary;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.pending.event.PendingDecryptEvent;
import com.example.procedure.processing.pending.event.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralizes message-pipeline reporting concerns such as stage events and structured logs.
 */
@Component
public class MessageProcessingReporter {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingReporter.class);

    private final MessageStageEventPublisher stageEventPublisher;
    private final PendingDecryptEventPublisher pendingDecryptEventPublisher;
    private final PendingDecryptQueue pendingDecryptQueue;
    private final MessageProcessingResultAssembler resultAssembler;

    public MessageProcessingReporter(
            MessageStageEventPublisher stageEventPublisher,
            PendingDecryptEventPublisher pendingDecryptEventPublisher,
            PendingDecryptQueue pendingDecryptQueue,
            MessageProcessingResultAssembler resultAssembler
    ) {
        this.stageEventPublisher = stageEventPublisher;
        this.pendingDecryptEventPublisher = pendingDecryptEventPublisher;
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.resultAssembler = resultAssembler;
    }

    /**
     * Publishes one stage event using the shared runtime context as the canonical source.
     */
    public void publishStageEvent(MessageProcessingContext context, String stageName) {
        stageEventPublisher.publish(context.toStageEvent(stageName));
    }

    /**
     * Publishes the pending-enqueue event after a message is parked in the retry queue.
     */
    public void publishPendingEnqueuedEvent(
            MessageProcessingContext context,
            DecryptAttemptResult decryptResult
    ) {
        MessageObservationSnapshot snapshot = context.toObservationSnapshot();

        PendingDecryptEvent event = PendingDecryptEvent.fromObservation(
                "pending-enqueued",
                snapshot,
                decryptResult == null ? null : decryptResult.getReason(),
                decryptResult == null ? null : decryptResult.getError(),
                pendingDecryptQueue.size(snapshot.getUeId()),
                1
        );

        pendingDecryptEventPublisher.publish(event);
    }

    /**
     * Logs the entry into the main chain with enough metadata to reconstruct the source of work.
     */
    public void logMainEntry(MessageProcessingContext context) {
        log.debug("Message main-chain enter: {}, enc={}, encType={}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.context(context),
                context.isEncrypted(),
                StageLogRefs.safe(context.getEncryptedType()),
                context.getSourceType(),
                StageLogRefs.safe(context.getSourceName()),
                context.getCorrelationId(),
                context.isReentry());
    }

    /**
     * Logs the early-return branch using the same result summary format as the normal exit path.
     */
    public void logMainEarlyReturn(MessageProcessingContext context, MessageProcessingResult result) {
        MessageProcessingSummary summary = resultAssembler.summarize(result);

        log.debug("Message main-chain early-return: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                summary.toLogString(),
                context.getCorrelationId());
    }

    /**
     * Logs the normal pipeline exit with a stable result summary for later searching and debugging.
     */
    public void logMainExit(MessageProcessingContext context, MessageProcessingResult result) {
        MessageProcessingSummary summary = resultAssembler.summarize(result);

        log.debug("Message main-chain exit: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                summary.toLogString(),
                context.getCorrelationId());
    }
}
