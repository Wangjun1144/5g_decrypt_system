package com.example.procedure.processing.message;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.decrypt.MessageDecryptStage;
import com.example.procedure.processing.message.result.MessageProcessingResultAssembler;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Encapsulates the decrypt-related control flow that used to live directly inside the main coordinator.
 */
@Component
public class MessageDecryptFlow {

    private static final Logger log = LoggerFactory.getLogger(MessageDecryptFlow.class);

    private final MessageDecryptStage messageDecryptStage;
    private final PendingDecryptQueue pendingDecryptQueue;
    private final MessageProcessingResultAssembler resultAssembler;
    private final MessageProcessingReporter reporter;

    public MessageDecryptFlow(
            MessageDecryptStage messageDecryptStage,
            PendingDecryptQueue pendingDecryptQueue,
            MessageProcessingResultAssembler resultAssembler,
            MessageProcessingReporter reporter
    ) {
        this.messageDecryptStage = messageDecryptStage;
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.resultAssembler = resultAssembler;
        this.reporter = reporter;
    }

    /**
     * Runs the decrypt phase and returns an early result when the main chain must stop here.
     */
    public MessageDecryptOutcome handle(
            MessageProcessingContext context,
            Function<MessageProcessingRequest, MessageProcessingResult> reentryProcessor
    ) {
        DecryptAttemptResult decryptResult =
                messageDecryptStage.handleEncryptedMessageIfNeeded(context);

        // Null keeps the main chain on its normal path because decrypt had nothing to short-circuit.
        if (decryptResult == null) {
            reporter.publishStageEvent(context, "message-decrypt-skip");

            log.debug("Message stage[decrypt] skipped-early-exit: {}, enc={}, encType={}, correlationId={}, reentry={}",
                    StageLogRefs.context(context),
                    context.isEncrypted(),
                    StageLogRefs.safe(context.getEncryptedType()),
                    context.getCorrelationId(),
                    context.isReentry());
            return MessageDecryptOutcome.continueMainChain(null);
        }

        reporter.publishStageEvent(context, "message-decrypt-result");

        log.debug("Message stage[decrypt] result: {}, encType={}, status={}, reason={}, error={}, correlationId={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getStatus(),
                decryptResult.getReason(),
                decryptResult.getError(),
                context.getCorrelationId());

        if (context.isDecryptOk()) {
            return handleDecryptSuccess(context, reentryProcessor);
        }

        if (context.isDecryptWaiting()) {
            enqueuePendingDecrypt(context, decryptResult);
            return MessageDecryptOutcome.earlyReturn(
                    resultAssembler.build(context),
                    decryptResult,
                    false,
                    true
            );
        }

        // Failed/skip decrypt attempts do not short-circuit the current pass.
        return MessageDecryptOutcome.continueMainChain(decryptResult);
    }

    /**
     * Handles the decrypt-success branch, including optional reentry into a fresh main-chain pass.
     */
    private MessageDecryptOutcome handleDecryptSuccess(
            MessageProcessingContext context,
            Function<MessageProcessingRequest, MessageProcessingResult> reentryProcessor
    ) {
        boolean reentered = messageDecryptStage.handleDecryptSuccess(context);

        reporter.publishStageEvent(context, "message-decrypt-success");

        log.debug("Message stage[decrypt-success] handled: {}, reentered={}, correlationId={}",
                StageLogRefs.context(context),
                reentered,
                context.getCorrelationId());

        if (reentered) {
            log.info("Message main-chain reenter: {}, correlationId={}, sourceType={}",
                    StageLogRefs.context(context),
                    context.getCorrelationId(),
                    context.getSourceType());

            return MessageDecryptOutcome.earlyReturn(
                    reentryProcessor.apply(
                    MessageProcessingRequest.reentry(
                            context.getMessage(),
                            deriveReentrySourceName(context),
                            context.getCorrelationId()
                    )
                    ),
                    context.getDecryptResult(),
                    true,
                    false
            );
        }

        return MessageDecryptOutcome.earlyReturn(
                resultAssembler.build(context),
                context.getDecryptResult(),
                false,
                false
        );
    }

    /**
     * Enqueues the current message into pending decrypt and publishes the matching observability events.
     */
    private void enqueuePendingDecrypt(
            MessageProcessingContext context,
            DecryptAttemptResult decryptResult
    ) {
        SignalingMessage msg = context.getMessage();

        pendingDecryptQueue.enqueue(
                msg.getUeId(),
                msg,
                decryptResult.getReason()
        );

        reporter.publishStageEvent(context, "message-pending-enqueue");
        reporter.publishPendingEnqueuedEvent(context, decryptResult);

        log.info("Message pending-enqueue: {}, encType={}, reason={}, correlationId={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getReason(),
                context.getCorrelationId());
    }

    /**
     * Derives the source name used by a reentered request so traces show the original provenance.
     */
    private String deriveReentrySourceName(MessageProcessingContext context) {
        String sourceName = context.getSourceName();
        if (sourceName == null || sourceName.isBlank()) {
            return "internal-reentry";
        }
        return sourceName + "#reentry";
    }
}
