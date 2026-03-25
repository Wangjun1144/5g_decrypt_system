package com.example.procedure.processing.pending;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的 pending decrypt 事件发布器。
 *
 * 当前作用：
 * 1. 为高质量单体提供稳定的 pending 状态观测点
 * 2. 为未来 waiting state / retry worker 事件化提供可替换 publisher
 */
@Service
public class LoggingPendingDecryptEventPublisher implements PendingDecryptEventPublisher {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingPendingDecryptEventPublisher.class);

    /**
     * 发布一条 pending decrypt 事件。
     *
     * @param event pending decrypt 事件
     */
    @Override
    public void publish(PendingDecryptEvent event) {
        log.info(
                "Pending event published: eventId={}, eventType={}, action={}, publishedAtMs={}, correlationId={}, ueId={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, waitReason={}, encryptedType={}, error={}, queueSize={}, batchSize={}",
                event.getEventId(),
                event.getEventType(),
                event.getAction(),
                event.getPublishedAtMs(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.getWaitReason(),
                event.getEncryptedType(),
                event.getError(),
                event.getQueueSize(),
                event.getBatchSize()
        );
    }
}
