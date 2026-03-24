package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;

import java.util.function.Consumer;

/**
 * “消息绑定阶段”的正式入口接口。
 *
 * 当前设计目标：
 * 1. 为 UE 绑定阶段建立清晰、稳定的新边界
 * 2. 让新代码不再直接依赖旧的 callback 风格作为唯一入口
 * 3. 让 binding 阶段具备正式 request/result 语义
 *
 * 当前阶段策略：
 * - 正式入口为 BindingProcessRequest -> BindingResolutionResult
 * - 旧 callback 风格继续保留为兼容 default 方法
 */
public interface MessageBindingProcessor {

    /**
     * 正式入口：处理一条 binding 阶段请求，并返回绑定结果。
     *
     * @param request binding 请求对象
     * @return binding 阶段输出结果
     */
    BindingResolutionResult process(BindingProcessRequest request);

    /**
     * 兼容旧接口：输入裸消息，通过 downstream callback 向下游输出。
     *
     * 当前这个方法的存在，是为了避免一次性改动过多调用方。
     *
     * @param msg 当前消息
     * @param downstream 下游消费者
     */
    default void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        BindingResolutionResult result = process(
                new BindingProcessRequest(msg, null, null, null, false)
        );

        if (result.isBuffered()) {
            return;
        }

        for (SignalingMessage readyMsg : result.toDownstreamOrder()) {
            downstream.accept(readyMsg);
        }
    }
}
