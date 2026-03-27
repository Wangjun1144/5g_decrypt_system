package com.example.procedure.processing.procedure.stage;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.dispatch.ProcedureDispatchOutcome;
import com.example.procedure.processing.dispatch.ProcedureDispatchService;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.procedure.recognize.ProcedureRecognitionOutcome;
import com.example.procedure.processing.procedure.recognize.ProcedureRecognitionService;
import org.springframework.stereotype.Service;

/**
 * 流程处理阶段的默认实现。
 *
 * 当前职责：
 * 1. 判断当前消息是否需要进入流程识别。
 * 2. 如需要，则调用流程识别服务并把结果写回主链上下文。
 * 3. 基于上下文统一执行流程分发。
 */
@Service
public class CoordinatingProcedureProcessingStage implements ProcedureProcessingStage {
    // REFACTOR STEP: PROCEDURE_STAGE_SUBPACKAGE_REORG
    // REFACTOR STEP: PROCEDURE_ROLE_RENAME

    private final ProcedureRecognitionService procedureRecognitionService;
    private final ProcedureDispatchService procedureDispatchService;

    public CoordinatingProcedureProcessingStage(
            ProcedureRecognitionService procedureRecognitionService,
            ProcedureDispatchService procedureDispatchService
    ) {
        this.procedureRecognitionService = procedureRecognitionService;
        this.procedureDispatchService = procedureDispatchService;
    }

    @Override
    public ProcedureStageOutcome process(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        ProcedureRecognitionOutcome recognitionOutcome = null;

        if (context.isProcedureMessage()) {
            // Only procedure-driving messages need the explicit recognition pass.
            recognitionOutcome = procedureRecognitionService.recognizeDetailed(msg);
            context.setProcedureMatchResult(recognitionOutcome.getMatchResult());
        }

        // Dispatch stays centralized here so the coordinator only observes the stage outcome.
        ProcedureDispatchOutcome dispatchOutcome = procedureDispatchService.dispatch(context);
        return ProcedureStageOutcome.of(context.isProcedureMessage(), recognitionOutcome, dispatchOutcome);
    }
}
