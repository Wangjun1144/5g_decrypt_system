package com.example.procedure.context;

import com.example.procedure.processing.event.InternalEventEnvelope;
import com.example.procedure.processing.event.InternalEventEnvelopes;
import com.example.procedure.processing.event.InternalEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的 UEContext 更新事件发布器。
 *
 * 当前作用：
 * 1. 为高质量单体提供稳定的上下文更新观测点
 * 2. 为未来上下文服务独立、outbox、消息总线发布提供可替换实现
 * 3. 当前发布时统一包装成标准内部事件 envelope
 */
@Service
public class LoggingUeContextUpdatedEventPublisher implements UeContextUpdatedEventPublisher {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingUeContextUpdatedEventPublisher.class);

    /**
     * 发布一条 UEContext 更新事件。
     *
     * 当前实现会先把事件包装成统一 envelope，
     * 再输出公共元数据和 UEContext 更新特有字段。
     *
     * @param event UEContext 更新事件
     */
    @Override
    // REFACTOR STEP: INTERNAL_EVENT_ENVELOPE_UE_CONTEXT
    public void publish(UeContextUpdatedEvent event) {
        InternalEventEnvelope<UeContextUpdatedEvent> envelope = InternalEventEnvelopes.from(event);
        InternalEventMetadata meta = envelope.getMetadata();
        UeContextUpdatedEvent payload = envelope.getPayload();

        log.info(
                "Internal event published: eventId={}, eventType={}, publishedAtMs={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, action={}, created={}, updated={}, message={}",
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
                payload.getAction(),
                payload.isCreated(),
                payload.isUpdated(),
                payload.getMessage()
        );
    }
}
