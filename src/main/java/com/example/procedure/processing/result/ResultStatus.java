package com.example.procedure.processing.result;

/**
 * 统一结果状态枚举。
 *
 * 当前用途：
 * 1. 为不同服务边界结果提供统一状态词汇
 * 2. 避免每个结果类都用自己的 success/buffered/skipped 表达方式
 * 3. 为后续统一日志、审计、API 返回摘要提供基础状态模型
 */
public enum ResultStatus {

    /**
     * 操作已成功完成。
     */
    SUCCESS,

    /**
     * 操作被显式跳过。
     */
    SKIPPED,

    /**
     * 当前结果表示进入缓冲/等待，而不是失败。
     */
    BUFFERED,

    /**
     * 操作失败。
     */
    FAILED
}
