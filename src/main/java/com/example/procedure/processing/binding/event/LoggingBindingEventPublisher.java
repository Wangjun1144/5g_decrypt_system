package com.example.procedure.processing.binding.event;

import com.example.procedure.processing.event.InternalEventEnvelope;
import com.example.procedure.processing.event.InternalEventEnvelopes;
import com.example.procedure.processing.event.InternalEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的 binding 事件发布器。
 *
 * 当前作用：
 * 1. 为高质量单体提供稳定的 binding 阶段观测点
 * 2. 为未来事件驱动扩展提供可替换的 publisher 实现
 * 3. 当前发布时统一包装成标准内部事件 envelope
 */
@Service
public class LoggingBindingEventPublisher implements BindingEventPublisher {
    // REFACTOR STEP: BINDING_SUBPACKAGE_REORG

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingBindingEventPublisher.class);

    /**
     * 发布一条 binding 事件。
     *
     * 当前实现只做日志输出，
     * 但日志格式已经统一到标准内部事件 envelope 上。
     *
     * @param event binding 事件
     */
    @Override
    public void publish(BindingResolvedEvent event) {
        InternalEventEnvelope<BindingResolvedEvent> envelope = InternalEventEnvelopes.from(event);
        InternalEventMetadata meta = envelope.getMetadata();
        BindingResolvedEvent payload = envelope.getPayload();

        log.info(
                "Internal event published: eventId={}, eventType={}, publishedAtMs={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, buffered={}, readyCount={}, releasedCount={}",
                meta.getEventId(),
                meta.getEventType(),
                meta.getPublishedAtMs(),
                meta.getCorrelationId(),
                meta.getUeId(),
                meta.getProcedureId(),
                meta.getProcedureTypeCode(),
                meta.getMessageId(),
                meta.getMessageType(),
                meta.getFrameNo(),
                meta.getSourceType(),
                meta.getSourceName(),
                meta.isReentry(),
                payload.isBuffered(),
                payload.getReadyCount(),
                payload.getReleasedCount()
        );
    }
}
