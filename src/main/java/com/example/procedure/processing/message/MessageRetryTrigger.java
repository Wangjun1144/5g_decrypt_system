package com.example.procedure.processing.message;

import com.example.procedure.processing.context.UeContextService;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.retry.PendingDecryptRetryBatchResult;
import com.example.procedure.processing.message.retry.PendingDecryptRetryService;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Triggers pending-decrypt retry after the current message may have refreshed UE key material.
 */
@Component
public class MessageRetryTrigger {

    private static final Logger log = LoggerFactory.getLogger(MessageRetryTrigger.class);

    private final UeContextService ueContextService;
    private final PendingDecryptRetryService pendingDecryptRetryService;
    private final MessageProcessingReporter reporter;

    public MessageRetryTrigger(
            UeContextService ueContextService,
            PendingDecryptRetryService pendingDecryptRetryService,
            MessageProcessingReporter reporter
    ) {
        this.ueContextService = ueContextService;
        this.pendingDecryptRetryService = pendingDecryptRetryService;
        this.reporter = reporter;
    }

    /**
     * Refreshes UE context and then gives the retry service a chance to drain older pending decrypt work.
     */
    public MessageRetryOutcome trigger(
            MessageProcessingContext context,
            Function<MessageProcessingRequest, MessageProcessingResult> reentryProcessor
    ) {
        SignalingMessage msg = context.getMessage();
        UEContext refreshedContext = ueContextService.getContext(msg.getUeId());

        log.debug("Message stage[pending-retry] start: {}, hasRefreshedContext={}, correlationId={}",
                StageLogRefs.context(context),
                refreshedContext != null,
                context.getCorrelationId());

        PendingDecryptRetryBatchResult batchResult = pendingDecryptRetryService.retryPendingDecrypt(
                msg.getUeId(),
                refreshedContext,
                reentryProcessor::apply
        );

        reporter.publishStageEvent(context, "message-pending-retry");

        log.debug("Message stage[pending-retry] done: {}, refreshedContext={}, hasBatch={}, correlationId={}",
                StageLogRefs.context(context),
                refreshedContext != null,
                batchResult != null,
                context.getCorrelationId());

        return MessageRetryOutcome.of(refreshedContext != null, batchResult);
    }
}
