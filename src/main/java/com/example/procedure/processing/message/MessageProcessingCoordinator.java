package com.example.procedure.processing.message;

import com.example.procedure.processing.context.UeContextService;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.classify.MessageClassificationOutcome;
import com.example.procedure.processing.message.classify.MessageClassificationService;
import com.example.procedure.processing.message.result.MessageProcessingResultAssembler;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.processing.procedure.stage.ProcedureStageOutcome;
import com.example.procedure.processing.procedure.stage.ProcedureProcessingStage;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main coordinator for the per-message processing pipeline.
 *
 * It intentionally focuses on ordering and branching, while reporting and result shaping
 * are delegated to dedicated collaborators.
 */
@Service
public class MessageProcessingCoordinator {
    // REFACTOR STEP: MESSAGE_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_DECRYPT_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_CLASSIFY_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_RESULT_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_RETRY_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_MAIN_COORDINATOR_RENAME

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingCoordinator.class);

    private final UeContextService ueContextService;
    private final MessageClassificationService classificationService;
    private final ProcedureProcessingStage procedureProcessingStage;
    private final MessageDecryptFlow messageDecryptFlow;
    private final MessageRetryTrigger messageRetryTrigger;
    private final MessageProcessingResultAssembler resultAssembler;
    private final MessageProcessingReporter reporter;

    /**
     * Builds the message main-chain coordinator.
     */
    public MessageProcessingCoordinator(
            UeContextService ueContextService,
            MessageClassificationService classificationService,
            ProcedureProcessingStage procedureProcessingStage,
            MessageDecryptFlow messageDecryptFlow,
            MessageRetryTrigger messageRetryTrigger,
            MessageProcessingResultAssembler resultAssembler,
            MessageProcessingReporter reporter
    ) {
        this.ueContextService = ueContextService;
        this.classificationService = classificationService;
        this.procedureProcessingStage = procedureProcessingStage;
        this.messageDecryptFlow = messageDecryptFlow;
        this.messageRetryTrigger = messageRetryTrigger;
        this.resultAssembler = resultAssembler;
        this.reporter = reporter;
    }

    /**
     * Formal entry point for processing one message request.
     */
    public MessageProcessingResult process(MessageProcessingRequest request) {
        MessageProcessingContext context = initializeContext(request);

        reporter.logMainEntry(context);
        reporter.publishStageEvent(context, "message-main-enter");

        runClassificationStage(context);
        loadUeContext(context);

        MessageDecryptOutcome decryptOutcome = messageDecryptFlow.handle(context, this::process);
        if (decryptOutcome.shouldReturnCurrentResult()) {
            reporter.publishStageEvent(context, "message-main-early-return");
            reporter.logMainEarlyReturn(context, decryptOutcome.getResult());
            return decryptOutcome.getResult();
        }

        runProcedureStage(context);
        retryPendingDecrypt(context);

        MessageProcessingResult finalResult = assembleResult(context);
        reporter.publishStageEvent(context, "message-main-exit");
        reporter.logMainExit(context, finalResult);
        return finalResult;
    }

    /**
     * Creates the shared runtime context for one pass through the message pipeline.
     */
    private MessageProcessingContext initializeContext(MessageProcessingRequest request) {
        return new MessageProcessingContext(request);
    }

    /**
     * Runs classification and emits the corresponding stage event and trace log.
     */
    private void runClassificationStage(MessageProcessingContext context) {
        MessageClassificationOutcome outcome = classificationService.classify(context);

        reporter.publishStageEvent(context, "message-classification");

        log.debug("Message stage[classification] done: {}, category={}, sourceType={}, correlationId={}, reentry={}",
                StageLogRefs.message(context.getMessage()),
                outcome.getCategory(),
                context.getSourceType(),
                context.getCorrelationId(),
                context.isReentry());
    }

    /**
     * Loads the latest UE context before decrypt and procedure stages consume it.
     */
    private void loadUeContext(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        UEContext ueContext = ueContextService.getContext(msg.getUeId());
        context.setUeContext(ueContext);

        reporter.publishStageEvent(context, "message-load-ue-context");

        log.debug("Message stage[load-ue-context] done: {}, hasContext={}, correlationId={}",
                StageLogRefs.message(context.getMessage()),
                context.hasUeContext(),
                context.getCorrelationId());
    }

    /**
     * Runs the procedure stage after message classification/decrypt no longer needs to short-circuit.
     */
    private void runProcedureStage(MessageProcessingContext context) {
        ProcedureStageOutcome outcome = procedureProcessingStage.process(context);

        reporter.publishStageEvent(context, "message-procedure");

        log.debug("Message stage[procedure] done: {}, category={}, procedureMessage={}, hasMatch={}, dispatched={}, procedureId={}, procedureType={}, correlationId={}",
                StageLogRefs.message(context.getMessage()),
                context.getCategory(),
                outcome.isProcedureMessage(),
                outcome.hasMatchResult(),
                outcome.isDispatched(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode(),
                context.getCorrelationId());
    }

    /**
     * Triggers retry of older pending decrypt work after the current message may have refreshed UE keys.
     */
    private void retryPendingDecrypt(MessageProcessingContext context) {
        MessageRetryOutcome retryOutcome = messageRetryTrigger.trigger(context, this::process);

        log.debug("Message stage[pending-retry-outcome] done: {}, refreshedContext={}, hasBatch={}, correlationId={}",
                StageLogRefs.message(context.getMessage()),
                retryOutcome.hasRefreshedContext(),
                retryOutcome.hasBatchResult(),
                context.getCorrelationId());
    }

    /**
     * Delegates final outward result assembly to the dedicated assembler component.
     */
    private MessageProcessingResult assembleResult(MessageProcessingContext context) {
        return resultAssembler.build(context);
    }
}
