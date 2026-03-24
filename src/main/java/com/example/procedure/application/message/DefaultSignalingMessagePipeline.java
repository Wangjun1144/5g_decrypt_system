package com.example.procedure.application.message;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.domain.binding.UeBindingService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.BindingProcessRequest;
import com.example.procedure.processing.message.MessageProcessRequest;
import com.example.procedure.processing.message.MessageProcessor;
import com.example.procedure.support.logging.SignalingDumpWriter;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 单条消息主处理链的默认实现。
 *
 * 当前定位：
 * 1. 它是 application 层“单条消息统一入口”的默认实现
 * 2. 它负责把一条消息组织进入 binding 阶段，再进入主处理阶段
 * 3. 当前仍保持同步单体执行方式不变
 */
@Service
public class DefaultSignalingMessagePipeline implements SignalingMessagePipeline {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultSignalingMessagePipeline.class);

    /**
     * 当前调试输出路径。
     */
    private static final Path SIGNALING_DUMP_PATH = Paths.get("logs/signaling_dump.log");

    /**
     * UE 绑定服务。
     */
    private final UeBindingService ueBindingService;

    /**
     * 消息主处理器。
     */
    private final MessageProcessor messageProcessor;

    /**
     * 构造默认 pipeline 实现。
     *
     * @param ueBindingService UE 绑定服务
     * @param messageProcessor 消息主处理器
     */
    public DefaultSignalingMessagePipeline(
            UeBindingService ueBindingService,
            MessageProcessor messageProcessor
    ) {
        this.ueBindingService = ueBindingService;
        this.messageProcessor = messageProcessor;
    }

    /**
     * 正式入口：处理一条 pipeline 请求。
     *
     * @param request pipeline 请求对象
     */
    @Override
    public void process(SignalingMessagePipelineRequest request) {
        SignalingMessage msg = request.getMessage();

        logPipelineEntry(msg, request);

        try {
            enterPipeline(request);
            logPipelineExit(msg, request);
        } catch (ApplicationStageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-pipeline-root",
                    msg,
                    "Unhandled failure in signaling message pipeline",
                    e
            );
        }
    }

    /**
     * 进入 pipeline 主体。
     *
     * 当前阶段这一步只负责进入 binding。
     *
     * @param request pipeline 请求对象
     */
    private void enterPipeline(SignalingMessagePipelineRequest request) {
        enterBindingStage(request);
    }

    /**
     * 进入 binding 阶段。
     *
     * 当前会把 pipeline 元数据透传给 binding 层，
     * 这样 binding 阶段也具备正式 request 语义和事件发布能力。
     *
     * @param request pipeline 请求对象
     */
    private void enterBindingStage(SignalingMessagePipelineRequest request) {
        SignalingMessage msg = request.getMessage();

        log.debug("Pipeline stage[binding] enter: {}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.message(msg),
                request.getSourceType(),
                StageLogRefs.safe(request.getSourceName()),
                request.getCorrelationId(),
                request.isReentry());

        try {
            ueBindingService.handle(
                    BindingProcessRequest.fromPipelineRequest(request),
                    boundMsg -> enterMainProcessingStage(request, boundMsg)
            );

            log.debug("Pipeline stage[binding] exit: {}, correlationId={}",
                    StageLogRefs.message(msg),
                    request.getCorrelationId());
        } catch (ApplicationStageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-binding",
                    msg,
                    "Failed while entering binding stage",
                    e
            );
        }
    }

    /**
     * binding 完成后进入主处理阶段。
     *
     * @param pipelineRequest pipeline 请求对象
     * @param boundMsg 已完成 binding 的消息
     */
    private void enterMainProcessingStage(
            SignalingMessagePipelineRequest pipelineRequest,
            SignalingMessage boundMsg
    ) {
        processMessage(pipelineRequest, boundMsg);
        afterMessageProcessed(boundMsg);
    }

    /**
     * 调用消息主处理器。
     *
     * @param pipelineRequest pipeline 请求对象
     * @param msg 当前消息
     */
    private void processMessage(
            SignalingMessagePipelineRequest pipelineRequest,
            SignalingMessage msg
    ) {
        log.debug("Pipeline stage[main-processing] enter: {}, correlationId={}, sourceType={}, reentry={}",
                StageLogRefs.message(msg),
                pipelineRequest.getCorrelationId(),
                pipelineRequest.getSourceType(),
                pipelineRequest.isReentry());

        try {
            MessageProcessRequest processRequest = new MessageProcessRequest(
                    msg,
                    pipelineRequest.getSourceType(),
                    pipelineRequest.getSourceName(),
                    pipelineRequest.getCorrelationId(),
                    pipelineRequest.isReentry()
            );

            messageProcessor.process(processRequest);

            log.debug("Pipeline stage[main-processing] exit: {}, correlationId={}",
                    StageLogRefs.message(msg),
                    pipelineRequest.getCorrelationId());
        } catch (ApplicationStageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-main-processing",
                    msg,
                    "Failed while processing signaling message in main processor",
                    e
            );
        }
    }

    /**
     * 主处理完成后的收尾动作。
     *
     * 当前只保留调试输出。
     *
     * @param msg 当前消息
     */
    private void afterMessageProcessed(SignalingMessage msg) {
        writeDebugDump(msg);
    }

    /**
     * 写出当前调试 dump。
     *
     * @param msg 当前消息
     */
    private void writeDebugDump(SignalingMessage msg) {
        log.debug("Pipeline stage[debug-dump] enter: {}, output={}",
                StageLogRefs.message(msg),
                SIGNALING_DUMP_PATH);

        try {
            SignalingDumpWriter.write(msg, SIGNALING_DUMP_PATH, true);

            log.debug("Pipeline stage[debug-dump] exit: {}, output={}",
                    StageLogRefs.message(msg),
                    SIGNALING_DUMP_PATH);
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-debug-dump",
                    msg,
                    "Failed while writing signaling debug dump",
                    e
            );
        }
    }

    /**
     * 记录 pipeline 入口日志。
     *
     * @param msg 当前消息
     * @param request pipeline 请求对象
     */
    private void logPipelineEntry(SignalingMessage msg, SignalingMessagePipelineRequest request) {
        log.debug("Pipeline enter: {}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.message(msg),
                request.getSourceType(),
                StageLogRefs.safe(request.getSourceName()),
                request.getCorrelationId(),
                request.isReentry());
    }

    /**
     * 记录 pipeline 出口日志。
     *
     * @param msg 当前消息
     * @param request pipeline 请求对象
     */
    private void logPipelineExit(SignalingMessage msg, SignalingMessagePipelineRequest request) {
        log.debug("Pipeline exit: {}, correlationId={}",
                StageLogRefs.message(msg),
                request.getCorrelationId());
    }
}
