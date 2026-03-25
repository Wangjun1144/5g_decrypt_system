package com.example.procedure.processing.result;

/**
 * 统一结果元数据。
 *
 * 当前用途：
 * 1. 给不同类型的结果对象提供统一的摘要外形
 * 2. 让日志、审计、事件发布时可以使用同一套结果元数据
 * 3. 保留各自领域结果对象的细节字段，不强行把它们揉成一个类
 */
public class ResultMetadata {

    /**
     * 当前结果类型名称。
     */
    private final String resultType;

    /**
     * 统一状态。
     */
    private final ResultStatus status;

    /**
     * 当前主标识。
     *
     * 例如：
     * - procedureId
     * - ueId
     * - msgId
     */
    private final String primaryId;

    /**
     * 当前结果说明。
     */
    private final String message;

    /**
     * 构造统一结果元数据。
     *
     * @param resultType 当前结果类型名称
     * @param status 统一状态
     * @param primaryId 当前主标识
     * @param message 当前结果说明
     */
    public ResultMetadata(
            String resultType,
            ResultStatus status,
            String primaryId,
            String message
    ) {
        this.resultType = resultType;
        this.status = status;
        this.primaryId = primaryId;
        this.message = message;
    }

    /**
     * 获取结果类型名称。
     *
     * @return 结果类型名称
     */
    public String getResultType() {
        return resultType;
    }

    /**
     * 获取统一状态。
     *
     * @return 统一状态
     */
    public ResultStatus getStatus() {
        return status;
    }

    /**
     * 获取主标识。
     *
     * @return 主标识
     */
    public String getPrimaryId() {
        return primaryId;
    }

    /**
     * 获取结果说明。
     *
     * @return 结果说明
     */
    public String getMessage() {
        return message;
    }
}
