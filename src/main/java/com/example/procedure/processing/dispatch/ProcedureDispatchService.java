package com.example.procedure.processing.dispatch;

import com.example.procedure.context.UeContextService;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.MessageProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 流程分发服务。
 *
 * 当前阶段定位：
 * - 这是“流程处理阶段”中的分发组件
 * - 负责承接流程识别后的统一分发动作
 * - 当前先保留旧系统已有行为，不引入新的业务分支
 * - 同时为后续事件驱动演进收口内部事件发布边界
 */
@Service
public class ProcedureDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ProcedureDispatchService.class);

    private final UeContextService ueContextService;
    private final ProcedureDispatchEventPublisher eventPublisher;

    public ProcedureDispatchService(
            UeContextService ueContextService,
            ProcedureDispatchEventPublisher eventPublisher
    ) {
        this.ueContextService = ueContextService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 新的正式入口：直接接收主链上下文。
     *
     * 这样做的好处：
     * - 分发阶段可以直接访问来源元数据、流程匹配结果、消息本体
     * - 为后续事件发布、审计、异步分发打基础
     */
    public void dispatch(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        MessageCategory category = context.getCategory();
        String procedureId = context.getMatchedProcedureId();
        String procedureTypeCode = context.getMatchedProcedureTypeCode();

        log.info(
                "Dispatch msg. ueId={}, msgType={}, category={}, procedureType={}, procedureId={}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureTypeCode,
                procedureId,
                context.getSourceType(),
                context.getSourceName(),
                context.getCorrelationId(),
                context.isReentry()
        );

        if (shouldUpdateInitialAccessContext(category, procedureTypeCode)) {
            ueContextService.updateOnInitialAccess(msg, procedureId);
        }

        publishDispatchEvent(context);
    }

    /**
     * 兼容旧调用方式，避免一次性修改过多调用方。
     */
    public void dispatch(
            SignalingMessage msg,
            MessageCategory category,
            String procedureId,
            String procedureTypeCode
    ) {
        log.info(
                "Dispatch msg. ueId={}, msgType={}, category={}, procedureType={}, procedureId={}",
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureTypeCode,
                procedureId
        );

        if (shouldUpdateInitialAccessContext(category, procedureTypeCode)) {
            ueContextService.updateOnInitialAccess(msg, procedureId);
        }

        ProcedureDispatchedEvent event = new ProcedureDispatchedEvent(
                null,
                msg.getUeId(),
                procedureId,
                procedureTypeCode,
                category,
                null,
                null,
                false,
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                msg.getTimestamp(),
                "procedure-dispatch"
        );
        eventPublisher.publish(event);
    }

    private void publishDispatchEvent(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();

        ProcedureDispatchedEvent event = new ProcedureDispatchedEvent(
                context.getCorrelationId(),
                msg.getUeId(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode(),
                context.getCategory(),
                context.getSourceType(),
                context.getSourceName(),
                context.isReentry(),
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                msg.getTimestamp(),
                "procedure-dispatch"
        );

        eventPublisher.publish(event);
    }

    private boolean shouldUpdateInitialAccessContext(
            MessageCategory category,
            String procedureTypeCode
    ) {
        return category == MessageCategory.PROCEDURE_DRIVING
                && "IA".equalsIgnoreCase(procedureTypeCode);
    }
}
