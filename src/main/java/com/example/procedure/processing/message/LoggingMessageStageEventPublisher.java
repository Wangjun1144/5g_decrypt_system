package com.example.procedure.processing.message;

import com.example.procedure.processing.event.InternalEventEnvelope;
import com.example.procedure.processing.event.InternalEventEnvelopes;
import com.example.procedure.processing.event.InternalEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的主处理链阶段事件发布器。
 *
 * 当前作用：
 * 1. 为高质量单体提供稳定的阶段观测点
 * 2. 为未来事件驱动扩展提供可替换的 publisher 实现
 * 3. 当前发布时统一包装成标准内部事件 envelope
 */
@Service
public class LoggingMessageStageEventPublisher implements MessageStageEventPublisher {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingMessageStageEventPublisher.class);

    /**
     * 发布一条消息阶段事件。
     *
     * 当前实现会先把事件包装成统一的 envelope，
     * 再输出公共元数据和当前载荷特有字段。
     *
     * @param event 消息阶段事件
     */
    @Override
    public void publish(MessageStageEvent event) {
        InternalEventEnvelope<MessageStageEvent> envelope = InternalEventEnvelopes.from(event);
        InternalEventMetadata meta = envelope.getMetadata();
        MessageStageEvent payload = envelope.getPayload();

        log.info(
                "Internal event published: eventId={}, eventType={}, publishedAtMs={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, stage={}, category={}, encrypted={}, encryptedType={}",
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
                payload.getStageName(),
                payload.getCategory(),
                payload.isEncrypted(),
                payload.getEncryptedType()
        );
    }
}
