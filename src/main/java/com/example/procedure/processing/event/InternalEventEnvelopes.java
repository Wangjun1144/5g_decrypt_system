package com.example.procedure.processing.event;

import com.example.procedure.context.UeContextUpdatedEvent;
import com.example.procedure.processing.binding.BindingResolvedEvent;
import com.example.procedure.processing.dispatch.ProcedureDispatchedEvent;
import com.example.procedure.processing.message.MessageStageEvent;

/**
 * 内部事件 envelope 工厂。
 *
 * 当前用途：
 * 1. 统一把现有事件对象转换成标准 envelope
 * 2. 把公共元数据的拼装逻辑集中到一个地方
 * 3. 减少 publisher 中重复拼字段的样板代码
 */
public final class InternalEventEnvelopes {

    /**
     * 工具类不允许实例化。
     */
    private InternalEventEnvelopes() {
    }

    /**
     * 把消息阶段事件包装成标准 envelope。
     *
     * @param event 消息阶段事件
     * @return 标准 envelope
     */
    public static InternalEventEnvelope<MessageStageEvent> from(MessageStageEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 把流程分发事件包装成标准 envelope。
     *
     * @param event 流程分发事件
     * @return 标准 envelope
     */
    public static InternalEventEnvelope<ProcedureDispatchedEvent> from(ProcedureDispatchedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 把 binding 事件包装成标准 envelope。
     *
     * 注意：
     * - 当前 binding 事件还没有流程 ID、流程类型、消息时间戳
     * - 这些字段先保留为空，后续如果 binding 阶段补了上下文再继续填充
     *
     * @param event binding 事件
     * @return 标准 envelope
     */
    public static InternalEventEnvelope<BindingResolvedEvent> from(BindingResolvedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                null,
                null,
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                null,
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 把 UEContext 更新事件包装成标准 envelope。
     *
     * 这样做之后，UEContext 更新链就和 message / dispatch / binding 一样，
     * 都走统一的内部事件元数据结构。
     *
     * @param event UEContext 更新事件
     * @return 标准 envelope
     */
    // REFACTOR STEP: INTERNAL_EVENT_ENVELOPE_UE_CONTEXT
    public static InternalEventEnvelope<UeContextUpdatedEvent> from(UeContextUpdatedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                null,
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.getPublishedAtMs()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }
}
