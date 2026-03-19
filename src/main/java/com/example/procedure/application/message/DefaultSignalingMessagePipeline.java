package com.example.procedure.application.message;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.domain.binding.UeBindingService;
import com.example.procedure.model.SignalingMessage;
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
 * - 它是 application 层里“单条消息统一入口”的默认实现
 * - 它负责把一条已经解析完成的 SignalingMessage
 *   组织进入绑定阶段，再进入主处理阶段
 *
 * 当前处理顺序保持不变：
 * 1. 进入绑定阶段
 * 2. 绑定阶段若释放历史 pending，则先处理 released 消息
 * 3. 当前消息在绑定完成后进入主消息处理器
 * 4. 保留当前阶段调试输出
 *
 * 当前阶段日志：
 * - 已与 MessageProcessor 和 PcapBatchOrchestrator 对齐
 * - 引用信息统一复用 StageLogRefs
 *
 * 第 26 小步的重点：
 * - 把重复的 application 阶段异常构造逻辑统一改为使用 ApplicationStageErrors
 */
@Service
public class DefaultSignalingMessagePipeline implements SignalingMessagePipeline {

    private static final Logger log = LoggerFactory.getLogger(DefaultSignalingMessagePipeline.class);

    private static final Path SIGNALING_DUMP_PATH = Paths.get("logs/signaling_dump.log");

    private final UeBindingService ueBindingService;
    private final MessageProcessor messageProcessor;

    public DefaultSignalingMessagePipeline(
            UeBindingService ueBindingService,
            MessageProcessor messageProcessor
    ) {
        this.ueBindingService = ueBindingService;
        this.messageProcessor = messageProcessor;
    }

    @Override
    public void process(SignalingMessage msg) {
        logPipelineEntry(msg);

        try {
            enterPipeline(msg);
            logPipelineExit(msg);
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

    private void enterPipeline(SignalingMessage msg) {
        enterBindingStage(msg);
    }

    private void enterBindingStage(SignalingMessage msg) {
        log.debug("Pipeline stage[binding] enter: {}",
                StageLogRefs.message(msg));

        try {
            ueBindingService.handle(msg, this::enterMainProcessingStage);

            log.debug("Pipeline stage[binding] exit: {}",
                    StageLogRefs.message(msg));
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

    private void enterMainProcessingStage(SignalingMessage boundMsg) {
        processMessage(boundMsg);
        afterMessageProcessed(boundMsg);
    }

    private void processMessage(SignalingMessage msg) {
        log.debug("Pipeline stage[main-processing] enter: {}",
                StageLogRefs.message(msg));

        try {
            messageProcessor.process(msg);

            log.debug("Pipeline stage[main-processing] exit: {}",
                    StageLogRefs.message(msg));
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

    private void afterMessageProcessed(SignalingMessage msg) {
        writeDebugDump(msg);
    }

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

    private void logPipelineEntry(SignalingMessage msg) {
        log.debug("Pipeline enter: {}", StageLogRefs.message(msg));
    }

    private void logPipelineExit(SignalingMessage msg) {
        log.debug("Pipeline exit: {}", StageLogRefs.message(msg));
    }
}
