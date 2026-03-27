package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;

/**
 * 正式的流程评分结果对象。
 *
 * 当前定位：
 * 1. 这是流程识别子域里的运行期评分结果
 * 2. 负责承接某个流程与当前消息之间的最佳评分结果
 * 3. 保持结构简单，方便后续继续演进到独立评分组件
 */
public class ProcedureScoreResult {

    /**
     * 命中的流程。
     */
    private final Procedure procedure;

    /**
     * 对应得分。
     */
    private final Score score;

    /**
     * 构造评分结果。
     *
     * @param procedure 命中的流程
     * @param score 对应得分
     */
    public ProcedureScoreResult(Procedure procedure, Score score) {
        this.procedure = procedure;
        this.score = score;
    }

    public Procedure getProcedure() {
        return procedure;
    }

    public Score getScore() {
        return score;
    }
}
