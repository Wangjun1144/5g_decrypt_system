package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.BindingResolutionResult;
import com.example.procedure.processing.binding.BindingResolver;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * @deprecated 阶段 1 / 2 过渡门面。
 *
 * 说明：
 * - 文档明确指出 UeIdBinder 当前同时承担规则判断、状态管理、缓冲、
 *   就近绑定、flush 和下游回调，是主要复杂度热点之一。:contentReference[oaicite:2]{index=2}
 * - 当前阶段先保留旧类名与旧调用方式，避免主链路一次性大改
 * - 真正实现已迁移到 processing.binding.BindingResolver
 *
 * 当前兼容语义：
 * - 若本轮消息需要缓冲，则不调用 downstream
 * - 若本轮消息已可处理，则先下发 released pending，再下发当前消息
 */
@Deprecated
@Service
public class UeIdBinder {

    private final BindingResolver bindingResolver;

    public UeIdBinder(BindingResolver bindingResolver) {
        this.bindingResolver = bindingResolver;
    }

    /**
     * 兼容旧接口：
     * 输入一条消息，若可以继续处理，则通过 downstream 回调输出。
     */
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        BindingResolutionResult result = bindingResolver.resolve(msg);

        if (result.isBuffered()) {
            return;
        }

        for (SignalingMessage readyMsg : result.toDownstreamOrder()) {
            downstream.accept(readyMsg);
        }
    }
}