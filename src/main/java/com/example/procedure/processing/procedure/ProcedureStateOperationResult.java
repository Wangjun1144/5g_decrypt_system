package com.example.procedure.processing.procedure;

/**
 * 流程状态操作结果。
 *
 * 当前用途：
 * - 统一承接 create / update / end 等状态操作的返回结果
 * - 让新主链不再直接依赖 legacy Map 结构
 *
 * 后续演进：
 * - 如果未来 procedure 状态服务独立出去，这个类可以继续作为应用层/接口层 DTO
 */
public class ProcedureStateOperationResult {

    private final boolean success;
    private final String procedureId;
    private final String message;

    private ProcedureStateOperationResult(
            boolean success,
            String procedureId,
            String message
    ) {
        this.success = success;
        this.procedureId = procedureId;
        this.message = message;
    }

    public static ProcedureStateOperationResult success(String procedureId, String message) {
        return new ProcedureStateOperationResult(true, procedureId, message);
    }

    public static ProcedureStateOperationResult failure(String message) {
        return new ProcedureStateOperationResult(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public String getMessage() {
        return message;
    }
}
