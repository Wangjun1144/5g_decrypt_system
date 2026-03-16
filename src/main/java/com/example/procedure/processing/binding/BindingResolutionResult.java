package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 身份绑定阶段的输出结果。
 *
 * 设计目的：
 * 1. 对齐文档中 Stage 3 的输出语义：
 *    - 绑定后的消息
 *    - 待缓冲消息
 *    - 释放后的 pending 消息
 * 2. 为后续把 callback 风格逐步改造成结果对象风格做准备
 * 3. 当前阶段仍可由旧 UeIdBinder 继续包装成 downstream callback 方式
 */
public class BindingResolutionResult {

    /** 当前消息是否被缓冲；若为 true，当前轮不应继续下游处理 */
    private final boolean buffered;

    /** 当前已完成绑定、应继续进入下游的消息（通常就是当前消息） */
    private final List<SignalingMessage> readyMessages;

    /** 因本轮绑定成功而被释放的历史 pending 消息 */
    private final List<SignalingMessage> releasedMessages;

    private BindingResolutionResult(
            boolean buffered,
            List<SignalingMessage> readyMessages,
            List<SignalingMessage> releasedMessages
    ) {
        this.buffered = buffered;
        this.readyMessages = readyMessages == null ? List.of() : Collections.unmodifiableList(readyMessages);
        this.releasedMessages = releasedMessages == null ? List.of() : Collections.unmodifiableList(releasedMessages);
    }

    public static BindingResolutionResult buffered() {
        return new BindingResolutionResult(true, List.of(), List.of());
    }

    public static BindingResolutionResult ready(SignalingMessage current, List<SignalingMessage> released) {
        List<SignalingMessage> ready = new ArrayList<>();
        if (current != null) {
            ready.add(current);
        }
        return new BindingResolutionResult(false, ready, released);
    }

    public boolean isBuffered() {
        return buffered;
    }

    public List<SignalingMessage> getReadyMessages() {
        return readyMessages;
    }

    public List<SignalingMessage> getReleasedMessages() {
        return releasedMessages;
    }

    /**
     * 为兼容旧 callback 风格而提供的总输出顺序。
     *
     * 保持与原 UeIdBinder 一致：
     * 1. 先 flush 历史 pending
     * 2. 最后再处理当前消息
     */
    public List<SignalingMessage> toDownstreamOrder() {
        List<SignalingMessage> result = new ArrayList<>(releasedMessages.size() + readyMessages.size());
        result.addAll(releasedMessages);
        result.addAll(readyMessages);
        return result;
    }
}