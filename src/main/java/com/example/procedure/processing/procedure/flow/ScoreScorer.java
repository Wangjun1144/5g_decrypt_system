package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;

/**
 * 正式的流程评分函数边界。
 *
 * 当前定位：
 * 1. 这是流程识别子域里的运行期评分接口
 * 2. 允许识别器、handler、执行器之间通过统一评分函数协作
 * 3. 为后续替换评分策略或远程评分能力预留边界
 */
@FunctionalInterface
public interface ScoreScorer {

    /**
     * 计算当前消息对某个流程的评分。
     *
     * @param proc 当前流程
     * @param msgTs 当前消息时间戳
     * @param msg 当前消息
     * @return 评分结果
     */
    // REFACTOR STEP: FLOW_RUNTIME_REORG_PHASE3
    Score score(Procedure proc, long msgTs, SignalingMessage msg);
}
