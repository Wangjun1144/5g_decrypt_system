package com.example.procedure.processing.binding.event;

/**
 * 绑定阶段事件发布边界。
 *
 * 当前阶段：
 * 1. 单体内先通过接口收口“binding 事件发布”动作
 * 2. 默认实现仍然是日志发布，不引入异步行为
 *
 * 后续演进：
 * - Kafka publisher
 * - Redis Stream publisher
 * - 审计 publisher
 * - outbox publisher
 */
public interface BindingEventPublisher {

    /**
     * 发布一条 binding 阶段事件。
     *
     * @param event binding 事件
     */
    void publish(BindingResolvedEvent event);
}
