package com.example.procedure.processing.message;

import com.example.procedure.context.UeContextService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import com.example.procedure.processing.procedure.ProcedureProcessingStage;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 规范化后的消息处理主入口。
 *
 * 当前定位：
 * - 它是单条消息主处理链的编排器
 * - 正式入口为 MessageProcessRequest
 * - 兼容旧调用时，仍允许直接传入 SignalingMessage
 */
@Service
public class MessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessor.class);

    private final UeContextService ueContextService;
    private final MessageClassificationService classificationService;
    private final MessageDecryptStage messageDecryptStage;
    private final ProcedureProcessingStage procedureProcessingStage;
    private final PendingDecryptQueue pendingDecryptQueue;
    private final MessageProcessingResultFactory resultFactory;
    private final PendingRetryService pendingRetryService;
    private final MessageStageEventPublisher stageEventPublisher;

    public MessageProcessor(
            UeContextService ueContextService,
            MessageClassificationService classificationService,
            MessageDecryptStage messageDecryptStage,
            ProcedureProcessingStage procedureProcessingStage,
            PendingDecryptQueue pendingDecryptQueue,
            MessageProcessingResultFactory resultFactory,
            @Lazy PendingRetryService pendingRetryService,
            MessageStageEventPublisher stageEventPublisher
    ) {
        this.ueContextService = ueContextService;
        this.classificationService = classificationService;
        this.messageDecryptStage = messageDecryptStage;
        this.procedureProcessingStage = procedureProcessingStage;
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.resultFactory = resultFactory;
        this.pendingRetryService = pendingRetryService;
        this.stageEventPublisher = stageEventPublisher;
    }

    public MessageProcessingResult process(MessageProcessRequest request) {
        MessageProcessingContext context = initializeContext(request);

        logMainEntry(context);
        publishStageEvent(context, "message-main-enter");

        runClassificationStage(context);
        loadUeContext(context);

        MessageProcessingResult earlyResult = handleDecryptStage(context);
        if (earlyResult != null) {
            publishStageEvent(context, "message-main-early-return");
            logMainEarlyReturn(context, earlyResult);
            return earlyResult;
        }

        runProcedureStage(context);
        retryPendingDecrypt(context);

        MessageProcessingResult finalResult = buildResult(context);
        publishStageEvent(context, "message-main-exit");
        logMainExit(context, finalResult);
        return finalResult;
    }

    public MessageProcessingResult process(SignalingMessage msg) {
        return process(MessageProcessRequest.of(msg));
    }

    private MessageProcessingContext initializeContext(MessageProcessRequest request) {
        return new MessageProcessingContext(request);
    }

    private void runClassificationStage(MessageProcessingContext context) {
        classificationService.classify(context);

        publishStageEvent(context, "message-classification");

        log.debug("Message stage[classification] done: {}, category={}, sourceType={}, correlationId={}, reentry={}",
                StageLogRefs.context(context),
                context.getCategory(),
                context.getSourceType(),
                context.getCorrelationId(),
                context.isReentry());
    }

    private void loadUeContext(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        UEContext ueContext = ueContextService.getContext(msg.getUeId());
        context.setUeContext(ueContext);

        publishStageEvent(context, "message-load-ue-context");

        log.debug("Message stage[load-ue-context] done: {}, hasContext={}, correlationId={}",
                StageLogRefs.context(context),
                context.hasUeContext(),
                context.getCorrelationId());
    }

    private MessageProcessingResult handleDecryptStage(MessageProcessingContext context) {
        DecryptAttemptResult decryptResult =
                messageDecryptStage.handleEncryptedMessageIfNeeded(context);

        if (decryptResult == null) {
            publishStageEvent(context, "message-decrypt-skip");

            log.debug("Message stage[decrypt] skipped-early-exit: {}, enc={}, encType={}, correlationId={}, reentry={}",
                    StageLogRefs.context(context),
                    context.isEncrypted(),
                    StageLogRefs.safe(context.getEncryptedType()),
                    context.getCorrelationId(),
                    context.isReentry());
            return null;
        }

        publishStageEvent(context, "message-decrypt-result");

        log.debug("Message stage[decrypt] result: {}, encType={}, status={}, reason={}, error={}, correlationId={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getStatus(),
                decryptResult.getReason(),
                decryptResult.getError(),
                context.getCorrelationId());

        if (context.isDecryptOk()) {
            return handleDecryptSuccess(context);
        }

        if (context.isDecryptWaiting()) {
            enqueuePendingDecrypt(context, decryptResult);
            return buildResult(context);
        }

        return null;
    }

    private MessageProcessingResult handleDecryptSuccess(MessageProcessingContext context) {
        boolean reentered = messageDecryptStage.handleDecryptSuccess(context);

        publishStageEvent(context, "message-decrypt-success");

        log.debug("Message stage[decrypt-success] handled: {}, reentered={}, correlationId={}",
                StageLogRefs.context(context),
                reentered,
                context.getCorrelationId());

        if (reentered) {
            log.info("Message main-chain reenter: {}, correlationId={}, sourceType={}",
                    StageLogRefs.context(context),
                    context.getCorrelationId(),
                    context.getSourceType());

            return process(
                    MessageProcessRequest.reentry(
                            context.getMessage(),
                            deriveReentrySourceName(context),
                            context.getCorrelationId()
                    )
            );
        }

        return buildResult(context);
    }

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

        publishStageEvent(context, "message-pending-enqueue");

        log.info("Message pending-enqueue: {}, encType={}, reason={}, correlationId={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getReason(),
                context.getCorrelationId());
    }

    private void runProcedureStage(MessageProcessingContext context) {
        procedureProcessingStage.process(context);

        publishStageEvent(context, "message-procedure");

        log.debug("Message stage[procedure] done: {}, category={}, procedureId={}, procedureType={}, correlationId={}",
                StageLogRefs.context(context),
                context.getCategory(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode(),
                context.getCorrelationId());
    }

    private void retryPendingDecrypt(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        UEContext refreshedContext = ueContextService.getContext(msg.getUeId());

        log.debug("Message stage[pending-retry] start: {}, hasRefreshedContext={}, correlationId={}",
                StageLogRefs.context(context),
                refreshedContext != null,
                context.getCorrelationId());

        pendingRetryService.retryPendingDecrypt(
                msg.getUeId(),
                refreshedContext
        );

        publishStageEvent(context, "message-pending-retry");

        log.debug("Message stage[pending-retry] done: {}, correlationId={}",
                StageLogRefs.context(context),
                context.getCorrelationId());
    }

    private MessageProcessingResult buildResult(MessageProcessingContext context) {
        return resultFactory.build(context);
    }

    private void logMainEntry(MessageProcessingContext context) {
        log.debug("Message main-chain enter: {}, enc={}, encType={}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.context(context),
                context.isEncrypted(),
                StageLogRefs.safe(context.getEncryptedType()),
                context.getSourceType(),
                StageLogRefs.safe(context.getSourceName()),
                context.getCorrelationId(),
                context.isReentry());
    }

    private void logMainEarlyReturn(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain early-return: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                resultFactory.summary(result),
                context.getCorrelationId());
    }

    private void logMainExit(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain exit: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                resultFactory.summary(result),
                context.getCorrelationId());
    }

    private void publishStageEvent(MessageProcessingContext context, String stageName) {
        stageEventPublisher.publish(context.toStageEvent(stageName));
    }

    private String deriveReentrySourceName(MessageProcessingContext context) {
        String sourceName = context.getSourceName();
        if (sourceName == null || sourceName.isBlank()) {
            return "internal-reentry";
        }
        return sourceName + "#reentry";
    }
}
