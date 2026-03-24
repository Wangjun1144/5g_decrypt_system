package com.example.procedure.processing.message;

/**
 * 主处理链阶段事件发布边界。
 *
 * 当前阶段：
 * - 先在单体内收口为统一发布接口
 * - 默认实现仍然是本地日志，不引入异步行为
 */
public interface MessageStageEventPublisher {

    void publish(MessageStageEvent event);
}
