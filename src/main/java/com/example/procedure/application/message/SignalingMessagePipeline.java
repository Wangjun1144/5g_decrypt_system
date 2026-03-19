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
 * 当前阶段的职责边界：
 * - 输入是一条已经解析完成的 SignalingMessage
 * - 先进入绑定阶段
 * - 再进入主消息处理阶段
 * - 最后保留当前阶段所需的调试输出
 *
 * 当前阶段不负责：
 * - 不负责 pcap/tshark 解析
 * - 不负责消息格式转换
 * - 不负责上游消息来源管理
 *
 * 也就是说：
 * 这个接口只关心“一条消息进入主链之后怎么跑”。
 */
public interface SignalingMessagePipeline {

    void process(SignalingMessage msg);
}
