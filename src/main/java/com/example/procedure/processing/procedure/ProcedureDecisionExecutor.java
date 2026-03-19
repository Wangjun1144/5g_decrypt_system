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
 * 1. 将“流程识别后的执行动作”从 ProClassify_Service 中拆出来
 * 2. 区分“识别决策”和“执行决策”两个职责
 * 3. 为后续继续把旧 ProClassify_Service 拆薄做准备
 *
 * 当前职责：
 * - 将 FlowMatchDecision 真正落地为：
 *   1. 挂接到已有流程
 *   2. 新建流程
 *   3. 创建 UNKNOWN 流程
 *
 * 当前阶段约束：
 * - 不改变现有业务语义
 * - 不改变 ProManager_Service 的调用方式
 * - 不改变 ProcedureMatchResult 的返回结构
 *
 * 这一步的意义：
 * - ProClassify_Service 更聚焦于“识别”
 * - 流程创建/挂接/UNKNOWN 兜底统一收口
 * - 后续如果再继续拆流程领域服务，会更顺
 */
@Service
public class ProcedureDecisionExecutor {

    /**
     * 执行一次流程匹配决策。
     *
     * @param msg      当前消息
     * @param decision 已经做出的流程决策
     * @param nowMs    当前时间戳
     * @param ctx      流程执行上下文
     * @param scorer   当前沿用的流程评分器
     * @return 流程匹配结果
     */
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

    /**
     * 将当前消息挂接到已有流程。
     *
     * 当前语义保持不变：
     * - 先重新计算一次 score
     * - 再调用 handler.applyUpdate(...)
     * - 返回 successExisting
     */
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

    /**
     * 创建一条新的业务流程。
     *
     * 当前仍沿用 ProManager_Service.add_ActProcedure(...)。
     */
    private ProcedureMatchResult createNewProcedure(
            SignalingMessage msg,
            ProcedureTypeEnum typeEnum,
            FlowContext ctx
    ) {
        if (typeEnum == null) {
            return ProcedureMatchResult.error("procedure type is null when creating new procedure");
        }

        var created = ctx.proManagerService().add_ActProcedure(
                msg.getUeId(),
                typeEnum,
                msg.getMsgType()
        );

        if (!isCreateSuccess(created)) {
            return ProcedureMatchResult.error("failed to create " + typeEnum);
        }

        String procedureId = String.valueOf(created.get("procedureId"));
        return ProcedureMatchResult.successNew(procedureId, typeEnum);
    }

    /**
     * 创建 UNKNOWN 流程。
     *
     * 当前仍保持原有 UNKNOWN 兜底逻辑不变。
     */
    private ProcedureMatchResult createUnknownProcedure(
            SignalingMessage msg,
            FlowContext ctx
    ) {
        var created = ctx.proManagerService().add_ActProcedure(
                msg.getUeId(),
                ProcedureTypeEnum.UNKNOWN,
                msg.getMsgType()
        );

        if (!isCreateSuccess(created)) {
            return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
        }

        String procedureId = String.valueOf(created.get("procedureId"));
        return ProcedureMatchResult.successNew(procedureId, ProcedureTypeEnum.UNKNOWN);
    }

    /**
     * 判断创建流程是否成功。
     *
     * 当前约定保持与 ProManager_Service 现有返回结构一致：
     * - status == 0 表示成功
     */
    private boolean isCreateSuccess(java.util.Map<String, Object> created) {
        return created != null
                && (int) created.getOrDefault("status", 1) == 0;
    }
}
