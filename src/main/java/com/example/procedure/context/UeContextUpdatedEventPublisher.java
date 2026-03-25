package com.example.procedure.context;

/**
 * UEContext 更新事件发布边界。
 *
 * 当前阶段：
 * 1. 先在单体内收口上下文更新事件发布动作
 * 2. 默认实现仍然是日志发布，不引入异步复杂度
 */
public interface UeContextUpdatedEventPublisher {

    /**
     * 发布一条 UEContext 更新事件。
     *
     * @param event UEContext 更新事件
     */
    void publish(UeContextUpdatedEvent event);
}
