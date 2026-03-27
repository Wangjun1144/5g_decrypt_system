package com.example.procedure.processing.procedure.state;

import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.processing.result.ResultStatus;

/**
 * 流程状态操作结果。
 *
 * 当前用途：
 * - 统一承接 create / update / end 等状态操作的返回结果
 * - 让新主链不再直接依赖松散的 Map 结构
 *
 * 后续演进：
 * - 如果未来 procedure 状态服务独立出去，这个类可以继续作为应用层/接口层 DTO
 */
public class ProcedureStateOperationResult {
    // REFACTOR STEP: PROCEDURE_STATE_SUBPACKAGE_REORG

    /**
     * 当前操作是否成功。
     */
    private final boolean success;

    /**
     * 当前流程 ID。
     */
    private final String procedureId;

    /**
     * 当前结果说明。
     */
    private final String message;

    /**
     * 构造流程状态操作结果。
     *
     * @param success 是否成功
     * @param procedureId 流程 ID
     * @param message 结果说明
     */
    private ProcedureStateOperationResult(
            boolean success,
            String procedureId,
            String message
    ) {
        this.success = success;
        this.procedureId = procedureId;
        this.message = message;
    }

    /**
     * 构造成功结果。
     *
     * @param procedureId 流程 ID
     * @param message 结果说明
     * @return 成功结果
     */
    public static ProcedureStateOperationResult success(String procedureId, String message) {
        return new ProcedureStateOperationResult(true, procedureId, message);
    }

    /**
     * 构造失败结果。
     *
     * @param message 结果说明
     * @return 失败结果
     */
    public static ProcedureStateOperationResult failure(String message) {
        return new ProcedureStateOperationResult(false, null, message);
    }

    /**
     * 判断当前操作是否成功。
     *
     * @return true 表示成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取流程 ID。
     *
     * @return 流程 ID
     */
    public String getProcedureId() {
        return procedureId;
    }

    /**
     * 获取结果说明。
     *
     * @return 结果说明
     */
    public String getMessage() {
        return message;
    }

    /**
     * 转换成统一结果元数据。
     *
     * 这样做之后，procedure 状态结果就可以和其他结果对象共用统一摘要结构。
     *
     * @return 统一结果元数据
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        return new ResultMetadata(
                "ProcedureStateOperationResult",
                success ? ResultStatus.SUCCESS : ResultStatus.FAILED,
                procedureId,
                message
        );
    }
}
