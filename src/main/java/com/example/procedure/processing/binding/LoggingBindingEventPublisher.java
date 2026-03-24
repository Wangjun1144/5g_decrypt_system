package com.example.procedure.processing.binding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的 binding 事件发布器。
 *
 * 当前作用：
 * 1. 为高质量单体提供稳定的 binding 阶段观测点
 * 2. 为未来事件驱动扩展提供可替换的 publisher 实现
 */
@Service
public class LoggingBindingEventPublisher implements BindingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingBindingEventPublisher.class);

    /**
     * 发布一条 binding 事件。
     *
     * 当前实现只做日志输出，
     * 后续如果切换到消息总线，不需要改主链代码。
     *
     * @param event binding 事件
     */
    @Override
    public void publish(BindingResolvedEvent event) {
        log.info(
                "Binding event published: eventId={}, eventType={}, correlationId={}, ueId={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, buffered={}, readyCount={}, releasedCount={}",
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.isBuffered(),
                event.getReadyCount(),
                event.getReleasedCount()
        );
    }
}
