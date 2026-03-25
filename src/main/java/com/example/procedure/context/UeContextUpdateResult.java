package com.example.procedure.context;

import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.processing.result.ResultStatus;

/**
 * UEContext 更新结果。
 *
 * 当前用途：
 * 1. 让上下文更新链具备正式输出结果
 * 2. 让上层知道这次是否创建了新上下文、是否实际执行了更新
 * 3. 为后续审计、异步补偿、结果汇总提供稳定结果模型
 */
public class UeContextUpdateResult {

    /**
     * 当前 UE ID。
     */
    private final String ueId;

    /**
     * 是否创建了新的上下文。
     */
    private final boolean created;

    /**
     * 是否执行了更新。
     */
    private final boolean updated;

    /**
     * 当前流程 ID。
     */
    private final String procedureId;

    /**
     * 结果说明。
     */
    private final String message;

    /**
     * 构造 UEContext 更新结果。
     *
     * @param ueId 当前 UE ID
     * @param created 是否新建
     * @param updated 是否已更新
     * @param procedureId 当前流程 ID
     * @param message 结果说明
     */
    public UeContextUpdateResult(
            String ueId,
            boolean created,
            boolean updated,
            String procedureId,
            String message
    ) {
        this.ueId = ueId;
        this.created = created;
        this.updated = updated;
        this.procedureId = procedureId;
        this.message = message;
    }

    /**
     * 构造“跳过更新”结果。
     *
     * @param ueId UE ID
     * @param procedureId 流程 ID
     * @param message 结果说明
     * @return 更新结果
     */
    public static UeContextUpdateResult skipped(String ueId, String procedureId, String message) {
        return new UeContextUpdateResult(ueId, false, false, procedureId, message);
    }

    /**
     * 构造“已更新”结果。
     *
     * @param ueId UE ID
     * @param created 是否新建
     * @param procedureId 流程 ID
     * @param message 结果说明
     * @return 更新结果
     */
    public static UeContextUpdateResult updated(String ueId, boolean created, String procedureId, String message) {
        return new UeContextUpdateResult(ueId, created, true, procedureId, message);
    }

    /**
     * 获取 UE ID。
     *
     * @return UE ID
     */
    public String getUeId() {
        return ueId;
    }

    /**
     * 判断是否创建了新上下文。
     *
     * @return true 表示新建
     */
    public boolean isCreated() {
        return created;
    }

    /**
     * 判断是否执行了更新。
     *
     * @return true 表示已更新
     */
    public boolean isUpdated() {
        return updated;
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
     * 这里的状态语义是：
     * - updated=true -> SUCCESS
     * - updated=false -> SKIPPED
     *
     * @return 统一结果元数据
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        return new ResultMetadata(
                "UeContextUpdateResult",
                updated ? ResultStatus.SUCCESS : ResultStatus.SKIPPED,
                ueId,
                message
        );
    }
}
