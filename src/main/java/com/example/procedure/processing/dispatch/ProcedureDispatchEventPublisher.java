package com.example.procedure.processing.dispatch;

/**
 * 流程分发事件发布边界。
 *
 * 当前阶段定位：
 * - 单体内部先通过接口收口“事件发布”动作
 * - 默认实现先做本地日志/观测输出
 *
 * 后续演进：
 * - Kafka publisher
 * - Redis Stream publisher
 * - 审计表 publisher
 * - outbox publisher
 */
public interface ProcedureDispatchEventPublisher {

    void publish(ProcedureDispatchedEvent event);
}
