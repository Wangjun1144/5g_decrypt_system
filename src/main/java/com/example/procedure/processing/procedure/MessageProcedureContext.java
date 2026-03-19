package com.example.procedure.processing.procedure;

import com.example.procedure.processing.message.MessageProcessingContext;

/**
 * 流程阶段专用上下文。
 *
 * 设计目的：
 * - 不让流程阶段直接依赖 MessageProcessor 的全部内部细节
 * - 先通过一个轻量上下文对象传递当前消息处理所需信息
 * - 为后续如果流程阶段需要附加更多流程侧信息，保留扩展位置
 *
 * 当前阶段它只是一个很薄的包装对象，
 * 但这种“阶段上下文”的模式对于后续流式处理演进很有价值。
 */
public class MessageProcedureContext {

    private final MessageProcessingContext messageContext;

    public MessageProcedureContext(MessageProcessingContext messageContext) {
        this.messageContext = messageContext;
    }

    public MessageProcessingContext getMessageContext() {
        return messageContext;
    }
}
