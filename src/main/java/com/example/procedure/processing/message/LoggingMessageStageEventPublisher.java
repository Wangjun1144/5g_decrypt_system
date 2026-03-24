package com.example.procedure.processing.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的主处理链阶段事件发布器。
 *
 * 当前作用：
 * - 为高质量单体提供稳定的阶段观测点
 * - 为未来事件驱动扩展提供可替换的 publisher 实现
 */
@Service
public class LoggingMessageStageEventPublisher implements MessageStageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageStageEventPublisher.class);

    @Override
    public void publish(MessageStageEvent event) {
        log.info(
                "Message stage event published: eventId={}, stage={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, category={}, sourceType={}, sourceName={}, reentry={}, encrypted={}, encryptedType={}",
                event.getEventId(),
                event.getStageName(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getCategory(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.isEncrypted(),
                event.getEncryptedType()
        );
    }
}
