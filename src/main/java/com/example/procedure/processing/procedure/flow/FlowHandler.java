package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;

import java.util.List;

/**
 * 流程处理器运行时接口。
 *
 * 当前定位：
 * 1. 这是流程识别与执行子域中的正式 handler 边界
 * 2. 各个具体流程 handler 仍可暂时留在 flow.impl 包中实现它
 * 3. 先把接口收入口径统一到 processing.procedure.flow，再逐步迁实现
 */
public interface FlowHandler {

    ProcedureTypeEnum type();

    boolean isTrigger(SignalingMessage msg);

    boolean shouldCreate(ProcedureScoreResult procScoreResult, SignalingMessage msg);

    int mergeThreshold();

    ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer);

    void applyUpdate(String ueId, Procedure proc, Score score, SignalingMessage msg, long nowMs, FlowContext ctx);

    /** 给 scoreProcedure 用：当前流程是否有运行时评分规则（ProcedureRule） */
    // REFACTOR STEP: FLOW_HANDLER_REHOME
    boolean hasRule();
}
