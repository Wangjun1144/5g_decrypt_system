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
 * - 它自己不承载具体分类、解密、流程识别规则
 * - 它负责控制各处理阶段的进入顺序、提前返回时机和最终结果收口
 *
 * 当前主链顺序保持不变：
 * 1. 初始化处理上下文
 * 2. 执行分类阶段
 * 3. 加载 UEContext
 * 4. 执行解密阶段
 * 5. 若未提前结束，则执行流程阶段
 * 6. 触发 pending 解密重试
 * 7. 构造统一处理结果
 *
 * 当前阶段日志：
 * - 只记录关键阶段和结果概要
 * - 引用信息统一复用 StageLogRefs
 *
 * 第 27 小步的重点：
 * - 结果日志摘要不再由 MessageProcessor 手写拼接
 * - 改为统一委托给 MessageProcessingResultFactory
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

    public MessageProcessor(
            UeContextService ueContextService,
            MessageClassificationService classificationService,
            MessageDecryptStage messageDecryptStage,
            ProcedureProcessingStage procedureProcessingStage,
            PendingDecryptQueue pendingDecryptQueue,
            MessageProcessingResultFactory resultFactory,
            @Lazy PendingRetryService pendingRetryService
    ) {
        this.ueContextService = ueContextService;
        this.classificationService = classificationService;
        this.messageDecryptStage = messageDecryptStage;
        this.procedureProcessingStage = procedureProcessingStage;
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.resultFactory = resultFactory;
        this.pendingRetryService = pendingRetryService;
    }

    public MessageProcessingResult process(SignalingMessage msg) {
        MessageProcessingContext context = initializeContext(msg);

        logMainEntry(context);

        runClassificationStage(context);
        loadUeContext(context);

        MessageProcessingResult earlyResult = handleDecryptStage(context);
        if (earlyResult != null) {
            logMainEarlyReturn(context, earlyResult);
            return earlyResult;
        }

        runProcedureStage(context);
        retryPendingDecrypt(context);

        MessageProcessingResult finalResult = buildResult(context);
        logMainExit(context, finalResult);
        return finalResult;
    }

    private MessageProcessingContext initializeContext(SignalingMessage msg) {
        return new MessageProcessingContext(msg);
    }

    private void runClassificationStage(MessageProcessingContext context) {
        classificationService.classify(context);

        log.debug("Message stage[classification] done: {}, category={}",
                StageLogRefs.context(context),
                context.getCategory());
    }

    private void loadUeContext(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        UEContext ueContext = ueContextService.getContext(msg.getUeId());
        context.setUeContext(ueContext);

        log.debug("Message stage[load-ue-context] done: {}, hasContext={}",
                StageLogRefs.context(context),
                context.hasUeContext());
    }

    private MessageProcessingResult handleDecryptStage(MessageProcessingContext context) {
        DecryptAttemptResult decryptResult =
                messageDecryptStage.handleEncryptedMessageIfNeeded(context);

        if (decryptResult == null) {
            log.debug("Message stage[decrypt] skipped-early-exit: {}, enc={}, encType={}",
                    StageLogRefs.context(context),
                    context.isEncrypted(),
                    StageLogRefs.safe(context.getEncryptedType()));
            return null;
        }

        log.debug("Message stage[decrypt] result: {}, encType={}, status={}, reason={}, error={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getStatus(),
                decryptResult.getReason(),
                decryptResult.getError());

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

        log.debug("Message stage[decrypt-success] handled: {}, reentered={}",
                StageLogRefs.context(context),
                reentered);

        if (reentered) {
            log.info("Message main-chain reenter: {}",
                    StageLogRefs.context(context));
            return process(context.getMessage());
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

        log.info("Message pending-enqueue: {}, encType={}, reason={}",
                StageLogRefs.context(context),
                StageLogRefs.safe(context.getEncryptedType()),
                decryptResult.getReason());
    }

    private void runProcedureStage(MessageProcessingContext context) {
        procedureProcessingStage.process(context);

        log.debug("Message stage[procedure] done: {}, category={}, procedureId={}, procedureType={}",
                StageLogRefs.context(context),
                context.getCategory(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode());
    }

    private void retryPendingDecrypt(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        UEContext refreshedContext = ueContextService.getContext(msg.getUeId());

        log.debug("Message stage[pending-retry] start: {}, hasRefreshedContext={}",
                StageLogRefs.context(context),
                refreshedContext != null);

        pendingRetryService.retryPendingDecrypt(
                msg.getUeId(),
                refreshedContext
        );

        log.debug("Message stage[pending-retry] done: {}",
                StageLogRefs.context(context));
    }

    private MessageProcessingResult buildResult(MessageProcessingContext context) {
        return resultFactory.build(context);
    }

    private void logMainEntry(MessageProcessingContext context) {
        log.debug("Message main-chain enter: {}, enc={}, encType={}",
                StageLogRefs.context(context),
                context.isEncrypted(),
                StageLogRefs.safe(context.getEncryptedType()));
    }

    private void logMainEarlyReturn(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain early-return: {}, {}",
                StageLogRefs.context(context),
                resultFactory.summary(result));
    }

    private void logMainExit(MessageProcessingContext context, MessageProcessingResult result) {
        log.debug("Message main-chain exit: {}, {}",
                StageLogRefs.context(context),
                resultFactory.summary(result));
    }
}
