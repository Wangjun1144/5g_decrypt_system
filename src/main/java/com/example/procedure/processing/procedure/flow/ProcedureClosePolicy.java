package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;

/**
 * 流程关闭策略边界。
 *
 * 当前定位：
 * 1. 这是流程识别/推进子域里的运行期关闭策略接口
 * 2. 它不属于静态 rule 定义，而属于 flow 执行期决策
 * 3. 后续如果不同流程类型需要不同关闭策略，可以继续在这里扩展
 */
public interface ProcedureClosePolicy {

    /**
     * 判断当前流程是否可以关闭。
     *
     * @param procedure 当前流程
     * @param nowMs 当前时间戳
     * @return true 表示可以关闭
     */
    // REFACTOR STEP: RULE_FLOW_BOUNDARY
    boolean isReadyToClose(Procedure procedure, long nowMs);
}
