package com.example.procedure.processing.procedure.flow;

import com.example.procedure.processing.procedure.state.ProcedureStateService;

/**
 * 正式的流程运行时上下文。
 *
 * 当前定位：
 * 1. 这是流程识别子域里的运行时共享上下文对象
 * 2. 承接流程状态服务和关闭策略等执行期依赖
 * 3. 为后续把流程识别子域进一步模块化提供稳定上下文边界
 */
public record FlowContext(
        ProcedureStateService procedureStateService,
        // REFACTOR STEP: FLOW_RUNTIME_REORG_PHASE4
        ProcedureClosePolicy closeDecider
) {
}
