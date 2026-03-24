package com.example.procedure.processing.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于日志的流程分发事件发布器。
 *
 * 当前职责：
 * - 作为单体阶段的默认 publisher 实现
 * - 把正式事件对象转成稳定的日志输出
 *
 * 后续如果接入消息总线，只需要替换这个实现即可。
 */
@Service
public class LoggingProcedureDispatchEventPublisher implements ProcedureDispatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingProcedureDispatchEventPublisher.class);

    @Override
    public void publish(ProcedureDispatchedEvent event) {
        log.info(
                "Dispatch event published: eventId={}, eventType={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, category={}, sourceType={}, sourceName={}, reentry={}, msgId={}, msgType={}, frameNo={}, stage={}",
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getCategory(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getProcessingStage()
        );
    }
}
