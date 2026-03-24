package com.example.procedure.processing.procedure;

import com.example.procedure.flow.FlowContext;
import com.example.procedure.flow.FlowHandler;
import com.example.procedure.flow.FlowMatchDecision;
import com.example.procedure.flow.ScoreScorer;
import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * 流程决策执行器。
 *
 * 设计目的：
 * 1. 将“流程识别后的执行动作”从主识别逻辑中拆出
 * 2. 区分“识别决策”和“执行决策”两类职责
 * 3. 为后续继续细化 procedure 领域服务做准备
 *
 * 当前职责：
 * - 将 FlowMatchDecision 真正落地为：
 *   1. 挂接到已有流程
 *   2. 新建流程
 *   3. 创建 UNKNOWN 流程
 *
 * 当前阶段约束：
 * - 不改变现有业务语义
 * - 不改变 ProcedureMatchResult 的返回结构
 */
@Service
public class ProcedureDecisionExecutor {

    public ProcedureMatchResult apply(
            SignalingMessage msg,
            FlowMatchDecision decision,
            long nowMs,
            FlowContext ctx,
            ScoreScorer scorer
    ) {
        if (decision == null) {
            return ProcedureMatchResult.error("flow decision is null");
        }

        return switch (decision.getAction()) {
            case ATTACH -> attachToExistingProcedure(msg, decision, nowMs, ctx, scorer);
            case CREATE -> createNewProcedure(msg, decision.getProcedureType(), ctx);
            case UNKNOWN -> createUnknownProcedure(msg, ctx);
        };
    }

    private ProcedureMatchResult attachToExistingProcedure(
            SignalingMessage msg,
            FlowMatchDecision decision,
            long nowMs,
            FlowContext ctx,
            ScoreScorer scorer
    ) {
        FlowHandler handler = decision.getHandler();
        Procedure procedure = decision.getProcedure();

        if (handler == null || procedure == null) {
            return ProcedureMatchResult.error("attach decision missing handler or procedure");
        }

        Score score = scorer.score(procedure, nowMs, msg);
        if (score == null) {
            return ProcedureMatchResult.error("score is null when attaching procedure");
        }

        handler.applyUpdate(
                msg.getUeId(),
                procedure,
                score,
                msg,
                nowMs,
                ctx
        );

        return ProcedureMatchResult.successExisting(
                procedure.getProcedureId(),
                handler.type()
        );
    }

    private ProcedureMatchResult createNewProcedure(
            SignalingMessage msg,
            ProcedureTypeEnum typeEnum,
            FlowContext ctx
    ) {
        if (typeEnum == null) {
            return ProcedureMatchResult.error("procedure type is null when creating new procedure");
        }

        ProcedureStateOperationResult created = ctx.procedureStateService().createProcedure(
                msg.getUeId(),
                typeEnum,
                msg.getMsgType()
        );

        if (!created.isSuccess()) {
            return ProcedureMatchResult.error("failed to create " + typeEnum + ": " + created.getMessage());
        }

        return ProcedureMatchResult.successNew(created.getProcedureId(), typeEnum);
    }

    private ProcedureMatchResult createUnknownProcedure(
            SignalingMessage msg,
            FlowContext ctx
    ) {
        ProcedureStateOperationResult created = ctx.procedureStateService().createProcedure(
                msg.getUeId(),
                ProcedureTypeEnum.UNKNOWN,
                msg.getMsgType()
        );

        if (!created.isSuccess()) {
            return ProcedureMatchResult.error("failed to create UNKNOWN procedure: " + created.getMessage());
        }

        return ProcedureMatchResult.successNew(
                created.getProcedureId(),
                ProcedureTypeEnum.UNKNOWN
        );
    }
}
