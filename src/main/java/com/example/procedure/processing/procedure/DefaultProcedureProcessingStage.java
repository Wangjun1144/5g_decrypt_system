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
 *
 * 第 10 小步的关键变化：
 * - 当前阶段开始直接围绕 MessageProcessingContext 工作
 * - 不再由 MessageProcessor 自己接收返回值再写回 context
 *
 * 这样做的意义：
 * - 流程阶段更像真正的“处理阶段”
 * - 主链编排器 MessageProcessor 更瘦
 * - 上下文作为阶段共享载体的角色更明确
 *
 * 对未来架构演进的意义：
 * - 当前它仍然是单体内部阶段
 * - 但其输入输出语义已经更像真正的 pipeline stage
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

        // 只有流程相关消息才进入流程识别。
        if (context.isProcedureMessage()) {
            ProcedureMatchResult matchResult = procedureRecognitionService.recognize(msg);
            context.setProcedureMatchResult(matchResult);
        }

        // 无论是否识别成功，都统一执行流程分发。
        //
        // 当前分发参数从上下文语义方法读取，
        // 这样可以把“流程结果的解释逻辑”收敛到 context 中。
        procedureDispatchService.dispatch(
                msg,
                context.getCategory(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode()
        );
    }
}
