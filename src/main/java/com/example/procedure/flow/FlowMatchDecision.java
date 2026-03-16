package com.example.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;

/**
 * 流程匹配最终决策
 *
 * 说明：
 * 你的项目当前并不是由 handler 自己创建 Procedure，
 * 而是由 ProManager_Service.add_ActProcedure(...) 统一创建。
 *
 * 所以这里的 CREATE 决策，只需要保存“要创建哪种流程类型”即可。
 */
public class FlowMatchDecision {

    public enum Action {
        ATTACH,   // 归并到已有流程
        CREATE,   // 创建新流程
        UNKNOWN   // 创建 UNKNOWN 或兜底
    }

    private final Action action;
    private final FlowHandler handler;
    private final Procedure procedure;
    private final ProcedureTypeEnum procedureType;

    private FlowMatchDecision(Action action,
                              FlowHandler handler,
                              Procedure procedure,
                              ProcedureTypeEnum procedureType) {
        this.action = action;
        this.handler = handler;
        this.procedure = procedure;
        this.procedureType = procedureType;
    }

    public static FlowMatchDecision attach(FlowHandler handler, Procedure procedure) {
        return new FlowMatchDecision(Action.ATTACH, handler, procedure, null);
    }

    public static FlowMatchDecision create(FlowHandler handler) {
        return new FlowMatchDecision(Action.CREATE, handler, null, handler.type());
    }

    public static FlowMatchDecision unknown() {
        return new FlowMatchDecision(Action.UNKNOWN, null, null, ProcedureTypeEnum.UNKNOWN);
    }

    public Action getAction() {
        return action;
    }

    public FlowHandler getHandler() {
        return handler;
    }

    public Procedure getProcedure() {
        return procedure;
    }

    public ProcedureTypeEnum getProcedureType() {
        return procedureType;
    }
}