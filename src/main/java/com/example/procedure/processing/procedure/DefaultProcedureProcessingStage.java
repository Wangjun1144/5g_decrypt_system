package com.example.procedure.processing.procedure;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.dispatch.ProcedureDispatchService;
import com.example.procedure.processing.message.MessageProcessingContext;
import org.springframework.stereotype.Service;

/**
 * 流程处理阶段的默认实现。
 *
 * 当前职责：
 * 1. 判断当前消息是否需要进入流程识别
 * 2. 若需要，则调用流程识别服务
 * 3. 将流程识别结果写回主链共享上下文
 * 4. 基于上下文统一执行流程分发
 */
@Service
public class DefaultProcedureProcessingStage implements ProcedureProcessingStage {

    private final ProcedureRecognitionService procedureRecognitionService;
    private final ProcedureDispatchService procedureDispatchService;

    public DefaultProcedureProcessingStage(
            ProcedureRecognitionService procedureRecognitionService,
            ProcedureDispatchService procedureDispatchService
    ) {
        this.procedureRecognitionService = procedureRecognitionService;
        this.procedureDispatchService = procedureDispatchService;
    }

    @Override
    public void process(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();

        if (context.isProcedureMessage()) {
            ProcedureMatchResult matchResult = procedureRecognitionService.recognize(msg);
            context.setProcedureMatchResult(matchResult);
        }

        procedureDispatchService.dispatch(context);
    }
}
