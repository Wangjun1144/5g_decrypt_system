package com.example.procedure.application.message;

import com.example.procedure.model.SignalingMessage;

/**
 * 单条 SignalingMessage 进入主处理链的统一入口。
 *
 * 设计目标：
 * 1. 把“单条消息如何进入系统主链”从具体入口类型中抽离出来
 * 2. 让 pcap 批处理、未来流式消息、测试入口都可以复用同一条主链
 * 3. 为后续向流式处理、事件驱动、分布式微服务演进建立稳定入口边界
 *
 * 当前阶段职责边界：
 * - 正式入口为 SignalingMessagePipelineRequest
 * - 兼容旧调用方式时，仍允许直接传入 SignalingMessage
 */
public interface SignalingMessagePipeline {

    void process(SignalingMessagePipelineRequest request);

    default void process(SignalingMessage msg) {
        process(SignalingMessagePipelineRequest.of(msg));
    }
}
