package com.example.procedure.processing.message;

import com.example.procedure.context.UeContextService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.pending.PendingDecryptEvent;
import com.example.procedure.processing.pending.PendingDecryptEventPublisher;
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

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(MessageProcessor.class);

    /**
     * UE 上下文服务。
     */
    private final UeContextService ueContextService;

    /**
     * 消息分类服务。
     */
    private final MessageClassificationService classificationService;

    /**
     * 解密阶段服务。
     */
    private final MessageDecryptStage messageDecryptStage;

    /**
     * 流程处理阶段。
     */
    private final ProcedureProcessingStage procedureProcessingStage;

    /**
     * pending 解密队列。
     */
    private final PendingDecryptQueue pendingDecryptQueue;

    /**
     * 结果构造器。
     */
    private final MessageProcessingResultFactory resultFactory;

    /**
     * pending 重试服务。
     */
    private final PendingRetryService pendingRetryService;

    /**
     * 主处理链阶段事件发布器。
     */
    private final MessageStageEventPublisher stageEventPublisher;

    /**
     * pending decrypt 事件发布器。
     */
    private final PendingDecryptEventPublisher pendingDecryptEventPublisher;

    /**
     * 构造消息主处理器。
     *
     * @param ueContextService UE 上下文服务
     * @param classificationService 分类服务
     * @param messageDecryptStage 解密阶段
     * @param procedureProcessingStage 流程处理阶段
     * @param pendingDecryptQueue pending 解密队列
     * @param resultFactory 结果构造器
     * @param pendingRetryService pending 重试服务
     * @param stageEventPublisher 阶段事件发布器
     * @param pendingDecryptEventPublisher pending 事件发布器
     */
    public MessageProcessor(
            UeContextService ueContextService,
            MessageClassificationService classificationService,
            MessageDecryptStage messageDecryptStage,
            ProcedureProcessingStage procedureProcessingStage,
            PendingDecryptQueue pendingDecryptQueue,
            MessageProcessingResultFactory resultFactory,
            @Lazy PendingRetryService pendingRetryService,
            MessageStageEventPublisher stageEventPublisher,
            PendingDecryptEventPublisher pendingDecryptEventPublisher
    ) {
        this.ueContextService = ueContextService;
        this.classificationService = classificationService;
        this.messageDecryptStage = messageDecryptStage;
        this.procedureProcessingStage = procedureProcessingStage;
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.resultFactory = resultFactory;
        this.pendingRetryService = pendingRetryService;
        this.stageEventPublisher = stageEventPublisher;
        this.pendingDecryptEventPublisher = pendingDecryptEventPublisher;
    }

    /**
     * 正式入口：处理一条消息主链请求。
     *
     * @param request 消息处理请求
     * @return 处理结果
     */
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

    /**
     * 兼容入口：直接处理裸消息。
     *
     * @param msg 当前消息
     * @return 处理结果
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        return process(MessageProcessRequest.of(msg));
    }

    /**
     * 初始化处理上下文。
     *
     * @param request 消息处理请求
     * @return 新的处理上下文
     */
    private MessageProcessingContext initializeContext(MessageProcessRequest request) {
        return new MessageProcessingContext(request);
    }

    /**
     * 执行分类阶段。
     *
     * @param context 当前处理上下文
     */
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

    /**
     * 加载 UE 上下文。
     *
     * @param context 当前处理上下文
     */
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

    /**
     * 处理解密阶段。
     *
     * @param context 当前处理上下文
     * @return 如果当前阶段应提前结束，则返回结果；否则返回 null
     */
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

    /**
     * 处理解密成功后的重入逻辑。
     *
     * @param context 当前处理上下文
     * @return 当前阶段结果
     */
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

    /**
     * 把当前消息放入 pending 解密队列。
     *
     * 这里除了入队，还会补发一条正式的 pending 事件，
     * 让“消息为什么停在这里”可以被后续观测到。
     *
     * @param context 当前处理上下文
     * @param decryptResult 当前解密结果
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

        publishStageEvent(context, "message-pending-enqueue");
        publishPendingEnqueuedEvent(context, decryptResult);

        log.info("Message pending-enqueue: {}, encType={}, reason={}, correlationId={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getReason(),
                context.getCorrelationId());
    }

    /**
     * 执行流程阶段。
     *
     * @param context 当前处理上下文
     */
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

    /**
     * 执行 pending retry。
     *
     * @param context 当前处理上下文
     */
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

    /**
     * 构造最终结果。
     *
     * @param context 当前处理上下文
     * @return 最终处理结果
     */
    private MessageProcessingResult buildResult(MessageProcessingContext context) {
        return resultFactory.build(context);
    }

    /**
     * 记录主链入口日志。
     *
     * @param context 当前处理上下文
     */
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

    /**
     * 记录主链提前返回日志。
     *
     * @param context 当前处理上下文
     * @param result 当前处理结果
     */
    private void logMainEarlyReturn(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain early-return: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                resultFactory.summary(result),
                context.getCorrelationId());
    }

    /**
     * 记录主链完成日志。
     *
     * @param context 当前处理上下文
     * @param result 当前处理结果
     */
    private void logMainExit(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain exit: {}, {}, correlationId={}",
                StageLogRefs.context(context),
                resultFactory.summary(result),
                context.getCorrelationId());
    }

    /**
     * 发布一条主处理链阶段事件。
     *
     * @param context 当前处理上下文
     * @param stageName 阶段名称
     */
    private void publishStageEvent(MessageProcessingContext context, String stageName) {
        stageEventPublisher.publish(context.toStageEvent(stageName));
    }

    /**
     * 发布一条“进入 pending 等待队列”的事件。
     *
     * @param context 当前处理上下文
     * @param decryptResult 当前解密结果
     */
    private void publishPendingEnqueuedEvent(
            MessageProcessingContext context,
            DecryptAttemptResult decryptResult
    ) {
        SignalingMessage msg = context.getMessage();

        PendingDecryptEvent event = new PendingDecryptEvent(
                "pending-enqueued",
                context.getCorrelationId(),
                msg.getUeId(),
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                msg.getTimestamp(),
                context.getSourceType(),
                context.getSourceName(),
                context.isReentry(),
                decryptResult == null ? null : decryptResult.getReason(),
                context.getEncryptedType(),
                decryptResult == null ? null : decryptResult.getError(),
                pendingDecryptQueue.size(msg.getUeId()),
                1
        );

        pendingDecryptEventPublisher.publish(event);
    }

    /**
     * 推导回流后的来源名称。
     *
     * @param context 当前处理上下文
     * @return 回流来源名称
     */
    private String deriveReentrySourceName(MessageProcessingContext context) {
        String sourceName = context.getSourceName();
        if (sourceName == null || sourceName.isBlank()) {
            return "internal-reentry";
        }
        return sourceName + "#reentry";
    }
}
