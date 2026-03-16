package com.example.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;

/**
 * 全局 best-match 结果
 *
 * 用于在 ProClassify_Service 中承载：
 * - 命中的 handler
 * - 命中的 procedure
 * - 对应的 score
 */
public class FlowBestMatch {

    private final FlowHandler handler;
    private final Procedure procedure;
    private final Score score;

    public FlowBestMatch(FlowHandler handler, Procedure procedure, Score score) {
        this.handler = handler;
        this.procedure = procedure;
        this.score = score;
    }

    public FlowHandler getHandler() {
        return handler;
    }

    public Procedure getProcedure() {
        return procedure;
    }

    public Score getScore() {
        return score;
    }
}