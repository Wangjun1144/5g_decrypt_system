package com.example.procedure.processing.message;

import com.example.procedure.model.SignalingMessage;

/**
 * 单条消息进入 MessageProcessor 主处理链的请求对象。
 *
 * 当前用途：
 * - 统一承接消息主处理链的正式入口参数
 * - 让 MessageProcessor 不再直接暴露裸 SignalingMessage 作为唯一正式入口
 *
 * 后续演进：
 * - 可继续补充重入来源、优先级、追踪标识、处理模式等字段
 */
public class MessageProcessRequest {

    private final SignalingMessage message;

    public MessageProcessRequest(SignalingMessage message) {
        this.message = message;
    }

    public static MessageProcessRequest of(SignalingMessage message) {
        return new MessageProcessRequest(message);
    }

    public SignalingMessage getMessage() {
        return message;
    }
}
