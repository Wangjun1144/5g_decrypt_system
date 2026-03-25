package com.example.procedure.processing.pending;

/**
 * pending decrypt 事件发布边界。
 *
 * 当前阶段：
 * 1. 单体内先通过接口收口 pending 状态事件发布动作
 * 2. 默认实现仍然是日志发布，不引入异步行为
 */
public interface PendingDecryptEventPublisher {

    /**
     * 发布一条 pending decrypt 事件。
     *
     * @param event pending decrypt 事件
     */
    void publish(PendingDecryptEvent event);
}
