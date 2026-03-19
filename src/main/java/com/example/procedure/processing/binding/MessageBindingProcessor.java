package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;

import java.util.function.Consumer;

/**
 * “消息绑定阶段”的新入口接口。
 *
 * 设计目的：
 * 1. 为 UE 绑定阶段建立一个清晰、稳定的新边界。
 * 2. 让新代码不再直接依赖旧的 UeIdBinder。
 * 3. 为后续把“绑定阶段”演化成独立处理节点、事件消费者、
 *    或流式处理 stage 做准备。
 *
 * 当前语义约定：
 * - 输入一条 SignalingMessage
 * - 如果当前消息还不能确定 ueId，绑定阶段可以选择先缓冲，不向下游输出
 * - 如果当前消息已经具备下游处理条件，则按既定顺序向下游输出：
 *   先输出释放出的历史 pending 消息，再输出当前消息
 *
 * 这份接口当前只是“收口入口”，
 * 不要求现在就改变任何旧有绑定策略或状态模型。
 */
public interface MessageBindingProcessor {

    void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream);
}
