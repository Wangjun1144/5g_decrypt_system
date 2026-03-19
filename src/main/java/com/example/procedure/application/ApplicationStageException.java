package com.example.procedure.application;

/**
 * application 层阶段性异常。
 *
 * 设计目的：
 * 1. 给 application 层的各个处理阶段提供统一的异常表达
 * 2. 在不改变底层业务逻辑的前提下，补充“失败发生在哪个阶段”的信息
 * 3. 为后续接统一日志、监控、trace、分布式错误归因做准备
 *
 * 当前使用范围：
 * - pcap 批处理入口
 * - 单消息主链入口
 *
 * 当前阶段不追求复杂异常体系，
 * 只先把“阶段名 + 上下文标识 + 原始异常”这三件事表达清楚。
 */
public class ApplicationStageException extends RuntimeException {

    /**
     * 出错阶段名。
     *
     * 示例：
     * - pcap-parse
     * - message-binding
     * - message-main-processing
     * - message-debug-dump
     */
    private final String stage;

    /**
     * 当前阶段相关的业务标识。
     *
     * 示例：
     * - pcap 文件路径
     * - msgId
     * - ueId
     *
     * 当前阶段允许为空。
     */
    private final String reference;

    public ApplicationStageException(String stage, String reference, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.reference = reference;
    }

    public String getStage() {
        return stage;
    }

    public String getReference() {
        return reference;
    }
}
