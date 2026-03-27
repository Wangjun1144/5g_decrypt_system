package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;

/**
 * 正式的流程匹配决策对象。
 *
 * 当前定位：
 * 1. 这是流程识别子域里的运行期决策模型
 * 2. 区分 attach / create / unknown 三种动作
 * 3. 当前仍复用旧的 FlowHandler 接口，避免一次性迁移范围过大
 */
public class FlowMatchDecision {

    /**
     * 决策动作类型。
     */
    public enum Action {
        ATTACH,
        CREATE,
        UNKNOWN
    }

    /**
     * 当前动作。
     */
    private final Action action;

    /**
     * 命中的 handler。
     */
    private final FlowHandler handler;

    /**
     * 命中的流程。
     */
    private final Procedure procedure;

    /**
     * 待创建的流程类型。
     */
    private final ProcedureTypeEnum procedureType;

    /**
     * 构造决策对象。
     *
     * @param action 当前动作
     * @param handler 命中的 handler
     * @param procedure 命中的流程
     * @param procedureType 待创建流程类型
     */
    protected FlowMatchDecision(
            Action action,
            FlowHandler handler,
            Procedure procedure,
            ProcedureTypeEnum procedureType
    ) {
        this.action = action;
        this.handler = handler;
        this.procedure = procedure;
        this.procedureType = procedureType;
    }

    /**
     * 构造 attach 决策。
     *
     * @param handler 命中的 handler
     * @param procedure 命中的流程
     * @return 决策对象
     */
    public static FlowMatchDecision attach(FlowHandler handler, Procedure procedure) {
        return new FlowMatchDecision(Action.ATTACH, handler, procedure, null);
    }

    /**
     * 构造 create 决策。
     *
     * @param handler 命中的 handler
     * @return 决策对象
     */
    public static FlowMatchDecision create(FlowHandler handler) {
        return new FlowMatchDecision(Action.CREATE, handler, null, handler.type());
    }

    /**
     * 构造 unknown 决策。
     *
     * @return 决策对象
     */
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
