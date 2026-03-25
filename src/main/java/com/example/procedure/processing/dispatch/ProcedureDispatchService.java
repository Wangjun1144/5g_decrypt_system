package com.example.procedure.processing.dispatch;

import com.example.procedure.context.UeContextService;
import com.example.procedure.context.UeContextUpdateRequest;
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

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(ProcedureDispatchService.class);

    /**
     * UE 上下文服务。
     */
    private final UeContextService ueContextService;

    /**
     * 流程分发事件发布器。
     */
    private final ProcedureDispatchEventPublisher eventPublisher;

    /**
     * 构造流程分发服务。
     *
     * @param ueContextService UE 上下文服务
     * @param eventPublisher 流程分发事件发布器
     */
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
     * @param context 当前主链上下文
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
            // REFACTOR STEP: UE_CONTEXT_BOUNDARY
            ueContextService.process(new UeContextUpdateRequest(
                    msg,
                    procedureId,
                    context.getSourceType(),
                    context.getSourceName(),
                    context.getCorrelationId(),
                    context.isReentry()
            ));
        }

        publishDispatchEvent(context);
    }

    /**
     * 兼容旧调用方式，避免一次性修改过多调用方。
     *
     * @param msg 当前消息
     * @param category 当前消息分类
     * @param procedureId 当前流程 ID
     * @param procedureTypeCode 当前流程类型编码
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

    /**
     * 发布一条流程分发事件。
     *
     * @param context 当前主链上下文
     */
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

    /**
     * 判断当前消息是否需要触发 IA 上下文更新。
     *
     * @param category 当前消息分类
     * @param procedureTypeCode 当前流程类型编码
     * @return true 表示需要更新
     */
    private boolean shouldUpdateInitialAccessContext(
            MessageCategory category,
            String procedureTypeCode
    ) {
        return category == MessageCategory.PROCEDURE_DRIVING
                && "IA".equalsIgnoreCase(procedureTypeCode);
    }
}
