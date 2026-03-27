package com.example.procedure.application.message;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.application.binding.BindingApplicationOutcome;
import com.example.procedure.application.binding.BindingApplicationService;
import com.example.procedure.application.binding.BindingProcessRequest;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.SignalingDumpWriter;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Application-layer coordinator for one signaling message entering the system.
 *
 * Current responsibilities:
 * 1. Enter the binding stage first.
 * 2. Forward released messages into the main message coordinator.
 * 3. Keep debug dump and top-level stage error handling at the application edge.
 */
@Service
public class CoordinatingSignalingMessagePipeline implements SignalingMessagePipeline {

    private static final Logger log = LoggerFactory.getLogger(CoordinatingSignalingMessagePipeline.class);
    private static final Path SIGNALING_DUMP_PATH = Paths.get("logs/signaling_dump.log");

    private final BindingApplicationService bindingApplicationService;
    private final MessageApplicationService messageApplicationService;

    /**
     * Creates the application-layer message coordinator.
     *
     * @param bindingApplicationService binding-stage application entry for UE identity resolution
     * @param messageApplicationService formal message application entry for one message
     */
    public CoordinatingSignalingMessagePipeline(
            BindingApplicationService bindingApplicationService,
            MessageApplicationService messageApplicationService
    ) {
        this.bindingApplicationService = bindingApplicationService;
        this.messageApplicationService = messageApplicationService;
    }

    /**
     * Processes one ingress request at the application edge.
     */
    @Override
    public void process(SignalingMessageIngressRequest request) {
        processDetailed(request);
    }

    /**
     * Processes one ingress request at the application edge and returns the
     * typed pipeline outcome.
     */
    @Override
    public MessagePipelineOutcome processDetailed(SignalingMessageIngressRequest request) {
        SignalingMessage msg = request.getMessage();

        logPipelineEntry(msg, request);

        try {
            PipelineExecution execution = enterPipeline(request);
            logPipelineExit(msg, request);
            return MessagePipelineOutcome.of(
                    request,
                    execution.bindingOutcome(),
                    execution.messageOutcomes(),
                    execution.processedCount()
            );
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
     * Enters the internal pipeline flow for one message.
     */
    private PipelineExecution enterPipeline(SignalingMessageIngressRequest request) {
        return enterBindingStage(request);
    }

    /**
     * Enters the binding stage and forwards released messages into main processing.
     */
    private PipelineExecution enterBindingStage(SignalingMessageIngressRequest request) {
        SignalingMessage msg = request.getMessage();

        log.debug("Pipeline stage[binding] enter: {}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.message(msg),
                request.getSourceType(),
                StageLogRefs.safe(request.getSourceName()),
                request.getCorrelationId(),
                request.isReentry());

        try {
            BindingApplicationOutcome outcome = bindingApplicationService.processDetailed(
                    BindingProcessRequest.fromPipelineRequest(request)
            );

            List<MessageApplicationOutcome> messageOutcomes = new ArrayList<>();
            int processedCount = 0;
            for (SignalingMessage boundMsg : outcome.toDownstreamOrder()) {
                processedCount++;
                messageOutcomes.add(enterMainProcessingStage(request, boundMsg));
            }

            log.debug("Pipeline stage[binding] exit: {}, processedCount={}, correlationId={}",
                    StageLogRefs.message(msg),
                    processedCount,
                    request.getCorrelationId());
            return new PipelineExecution(outcome, messageOutcomes, processedCount);
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
     * Continues with the main processing stage after binding is completed.
     */
    private MessageApplicationOutcome enterMainProcessingStage(
            SignalingMessageIngressRequest pipelineRequest,
            SignalingMessage boundMsg
    ) {
        MessageApplicationOutcome outcome = processMessage(pipelineRequest, boundMsg);
        afterMessageProcessed(boundMsg);
        return outcome;
    }

    /**
     * Delegates one message to the main message processing coordinator.
     */
    private MessageApplicationOutcome processMessage(
            SignalingMessageIngressRequest pipelineRequest,
            SignalingMessage msg
    ) {
        log.debug("Pipeline stage[main-processing] enter: {}, correlationId={}, sourceType={}, reentry={}",
                StageLogRefs.message(msg),
                pipelineRequest.getCorrelationId(),
                pipelineRequest.getSourceType(),
                pipelineRequest.isReentry());

        try {
            SignalingMessageIngressRequest processRequest = new SignalingMessageIngressRequest(
                    msg,
                    pipelineRequest.getSourceType(),
                    pipelineRequest.getSourceName(),
                    pipelineRequest.getCorrelationId(),
                    pipelineRequest.isReentry()
            );

            MessageApplicationOutcome outcome =
                    messageApplicationService.processDetailed(processRequest);

            log.debug("Pipeline stage[main-processing] exit: {}, correlationId={}, reentry={}",
                    StageLogRefs.message(msg),
                    outcome.getCorrelationId(),
                    outcome.isReentry());
            return outcome;
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
     * Runs post-processing hooks after the main stage succeeds.
     */
    private void afterMessageProcessed(SignalingMessage msg) {
        writeDebugDump(msg);
    }

    /**
     * Writes the application-edge debug dump for the current message.
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
     * Writes the application-edge entry log.
     */
    private void logPipelineEntry(SignalingMessage msg, SignalingMessageIngressRequest request) {
        log.debug("Pipeline enter: {}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                StageLogRefs.message(msg),
                request.getSourceType(),
                StageLogRefs.safe(request.getSourceName()),
                request.getCorrelationId(),
                request.isReentry());
    }

    /**
     * Writes the application-edge exit log.
     */
    private void logPipelineExit(SignalingMessage msg, SignalingMessageIngressRequest request) {
        log.debug("Pipeline exit: {}, correlationId={}",
                StageLogRefs.message(msg),
                request.getCorrelationId());
    }

    /**
     * Small immutable carrier for one pipeline pass before it is exposed as the
     * outward application outcome.
     */
    private record PipelineExecution(
            BindingApplicationOutcome bindingOutcome,
            List<MessageApplicationOutcome> messageOutcomes,
            int processedCount
    ) {}
}
