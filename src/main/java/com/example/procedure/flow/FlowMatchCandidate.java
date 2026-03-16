package com.example.procedure.flow;

import com.example.procedure.model.Procedure;

/**
 * 流程匹配候选项
 *
 * 用于承载：
 * 1. 某个 handler 对某个 active procedure 的匹配评分
 * 2. 或某个 handler 判断“应该创建新流程”的候选信息
 *
 * 这样可以避免在 ProClassify_Service 里到处散落临时变量。
 */
public class FlowMatchCandidate {

    /** 参与匹配的 handler */
    private final FlowHandler handler;

    /** 命中的 active procedure；若为创建候选，则这里允许为 null */
    private final Procedure procedure;

    /** 匹配分数 */
    private final int score;

    /** 是否表示“应该创建新流程”的候选 */
    private final boolean createNew;

    public FlowMatchCandidate(FlowHandler handler, Procedure procedure, int score, boolean createNew) {
        this.handler = handler;
        this.procedure = procedure;
        this.score = score;
        this.createNew = createNew;
    }

    public static FlowMatchCandidate attach(FlowHandler handler, Procedure procedure, int score) {
        return new FlowMatchCandidate(handler, procedure, score, false);
    }

    public static FlowMatchCandidate create(FlowHandler handler, int score) {
        return new FlowMatchCandidate(handler, null, score, true);
    }

    public FlowHandler getHandler() {
        return handler;
    }

    public Procedure getProcedure() {
        return procedure;
    }

    public int getScore() {
        return score;
    }

    public boolean isCreateNew() {
        return createNew;
    }
}