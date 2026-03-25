package com.example.procedure.processing.dispatch;

import com.example.procedure.processing.event.InternalEventEnvelope;
import com.example.procedure.processing.event.InternalEventEnvelopes;
import com.example.procedure.processing.event.InternalEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的流程分发事件发布器。
 *
 * 当前职责：
 * 1. 作为单体阶段的默认 publisher 实现
 * 2. 把正式事件对象转成稳定的日志输出
 * 3. 当前发布时统一包装成标准内部事件 envelope
 */
@Service
public class LoggingProcedureDispatchEventPublisher implements ProcedureDispatchEventPublisher {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingProcedureDispatchEventPublisher.class);

    /**
     * 发布一条流程分发事件。
     *
     * @param event 流程分发事件
     */
    @Override
    public void publish(ProcedureDispatchedEvent event) {
        InternalEventEnvelope<ProcedureDispatchedEvent> envelope = InternalEventEnvelopes.from(event);
        InternalEventMetadata meta = envelope.getMetadata();
        ProcedureDispatchedEvent payload = envelope.getPayload();

        log.info(
                "Internal event published: eventId={}, eventType={}, publishedAtMs={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, category={}, stage={}",
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
                payload.getCategory(),
                payload.getProcessingStage()
        );
    }
}
