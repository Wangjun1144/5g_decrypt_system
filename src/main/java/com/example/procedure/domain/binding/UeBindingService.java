package com.example.procedure.domain.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.BindingProcessRequest;
import com.example.procedure.processing.binding.BindingResolutionResult;
import com.example.procedure.processing.binding.MessageBindingProcessor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 领域层视角下的 UE 绑定服务。
 *
 * 当前职责：
 * 1. 给上层编排逻辑提供更易理解的业务命名
 * 2. 隐藏底层具体 binding 实现细节
 * 3. 为 application 层提供“正式 request/result 风格”和“兼容 callback 风格”两种入口
 */
@Service
public class UeBindingService {

    /**
     * 新的 binding 正式入口。
     */
    private final MessageBindingProcessor bindingProcessor;

    /**
     * 构造领域层 binding 服务。
     *
     * @param bindingProcessor binding 正式处理器
     */
    public UeBindingService(MessageBindingProcessor bindingProcessor) {
        this.bindingProcessor = bindingProcessor;
    }

    /**
     * 正式入口：处理一个 binding 请求，返回 binding 结果。
     *
     * 推荐新代码优先使用这个入口，
     * 因为它更符合阶段化单体和未来事件驱动扩展的方向。
     *
     * @param request binding 请求
     * @return binding 结果
     */
    public BindingResolutionResult process(BindingProcessRequest request) {
        return bindingProcessor.process(request);
    }

    /**
     * 兼容入口：输入一条消息，通过 downstream callback 输出。
     *
     * 这个方法当前保留，是为了兼容还未迁完的调用方。
     *
     * @param msg 当前消息
     * @param downstream 下游消费者
     */
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        bindingProcessor.handle(msg, downstream);
    }

    /**
     * 兼容入口：输入带元数据的 binding 请求，仍然通过 downstream callback 输出。
     *
     * 这个方法用于 application 层逐步从旧 callback 风格迁移到正式 request/result 风格。
     *
     * @param request binding 请求
     * @param downstream 下游消费者
     */
    public void handle(BindingProcessRequest request, Consumer<SignalingMessage> downstream) {
        BindingResolutionResult result = bindingProcessor.process(request);

        if (result.isBuffered()) {
            return;
        }

        for (SignalingMessage readyMsg : result.toDownstreamOrder()) {
            downstream.accept(readyMsg);
        }
    }
}
