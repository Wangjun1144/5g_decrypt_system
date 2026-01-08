package com.example.procedure.flow;

import com.example.procedure.model.*;

import java.util.List;

public interface FlowHandler {

    ProcedureTypeEnum type();

    /** 触发器：是否走“该流程的优先归并通道” */
    boolean isTrigger(SignalingMessage msg);

    /** 新建门槛：优先归并失败后，是否允许创建该流程 */
    boolean shouldCreate(ProcedureScoreResult procScoreResult, SignalingMessage msg);

    /** 归并阈值（例如 IA=35，XHO=35） */
    int mergeThreshold();

    /** 从 activeList 中只挑本 type 的候选，返回 best（含 Score） */
    ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer);

    /** 归并/命中后如何更新流程上下文（keyMask/endSeen/phase推进/持久化/close） */
    void applyUpdate(String ueId, Procedure proc, Score score, SignalingMessage msg, long nowMs, FlowContext ctx);

    /** 给 scoreProcedure 用：当前流程是否有 rule（ProcedureRule） */
    boolean hasRule();
}
