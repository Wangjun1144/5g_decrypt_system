package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;

/**
 * 正式的全局 best-match 结果对象。
 *
 * 当前定位：
 * 1. 这是流程识别子域里的运行期匹配结果对象
 * 2. 用于承接跨 handler 的最佳命中结果
 * 3. 当前仍复用旧的 FlowHandler 接口，避免一次性迁移范围过大
 */
public class FlowBestMatch {

    /**
     * 命中的 handler。
     */
    private final FlowHandler handler;

    /**
     * 命中的流程。
     */
    private final Procedure procedure;

    /**
     * 对应得分。
     */
    private final Score score;

    /**
     * 构造 best-match 结果。
     *
     * @param handler 命中的 handler
     * @param procedure 命中的流程
     * @param score 对应得分
     */
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
