package com.example.procedure.rule;

import com.example.procedure.model.Procedure;
import com.example.procedure.processing.procedure.flow.ProcedureClosePolicy;
import org.springframework.stereotype.Component;

/**
 * @deprecated 旧的流程关闭判定器兼容层。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖 rule.ProcedureCloseDecider 这个名字
 * 2. 正式运行期关闭策略已经迁到 processing.procedure.flow 包
 * 3. 这里收缩为兼容壳，避免旧引用立即失效
 */
@Deprecated
@Component
public class ProcedureCloseDecider {

    /**
     * 正式流程关闭策略。
     */
    // REFACTOR STEP: RULE_FLOW_BOUNDARY
    private final ProcedureClosePolicy delegate;

    /**
     * 构造旧兼容层。
     *
     * @param delegate 正式流程关闭策略
     */
    public ProcedureCloseDecider(ProcedureClosePolicy delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：判断流程是否可以关闭。
     *
     * @param procedure 当前流程
     * @param nowMs 当前时间戳
     * @return true 表示可以关闭
     */
    public boolean isReadyToClose(Procedure procedure, long nowMs) {
        return delegate.isReadyToClose(procedure, nowMs);
    }
}
