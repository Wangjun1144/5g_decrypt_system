package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 绑定阶段的默认实现。
 *
 * 当前阶段定位：
 * 1. 作为新的 binding 正式入口实现
 * 2. 内部继续复用已经拆出的 BindingResolver
 * 3. 为 binding 阶段增加正式事件发布边界
 *
 * 当前行为保持不变：
 * - 如果结果是 buffered，则本轮不向下游输出
 * - 如果结果可继续处理，则先输出 released，再输出当前 ready 消息
 */
@Service
public class DefaultMessageBindingProcessor implements MessageBindingProcessor {

    /**
     * binding 核心决策器。
     */
    private final BindingResolver bindingResolver;

    /**
     * binding 阶段事件发布边界。
     */
    private final BindingEventPublisher eventPublisher;

    /**
     * 构造 binding 默认实现。
     *
     * @param bindingResolver binding 决策器
     * @param eventPublisher binding 事件发布器
     */
    public DefaultMessageBindingProcessor(
            BindingResolver bindingResolver,
            BindingEventPublisher eventPublisher
    ) {
        this.bindingResolver = bindingResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 正式入口：处理 binding 请求，返回 binding 结果。
     *
     * 这里的职责很明确：
     * 1. 调用 BindingResolver 计算 binding 结果
     * 2. 发布 binding 阶段事件
     * 3. 不在这里处理下游 callback
     *
     * @param request binding 请求对象
     * @return binding 结果
     */
    @Override
    public BindingResolutionResult process(BindingProcessRequest request) {
        SignalingMessage msg = request.getMessage();

        BindingResolutionResult result = bindingResolver.resolve(msg);

        publishBindingEvent(request, result);

        return result;
    }

    /**
     * 兼容旧接口：保留 callback 风格输出。
     *
     * 这样做的原因：
     * - 旧调用方仍然可以继续使用
     * - 但内部已经统一走新的正式入口
     *
     * @param msg 当前消息
     * @param downstream 下游处理器
     */
    @Override
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
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

    /**
     * 发布一条 binding 阶段事件。
     *
     * 当前只做本地日志 publisher，
     * 但事件边界已经建立起来了。
     *
     * @param request binding 请求
     * @param result binding 结果
     */
    private void publishBindingEvent(
            BindingProcessRequest request,
            BindingResolutionResult result
    ) {
        SignalingMessage msg = request.getMessage();

        BindingResolvedEvent event = new BindingResolvedEvent(
                request.getCorrelationId(),
                msg.getUeId(),
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                request.getSourceType(),
                request.getSourceName(),
                request.isReentry(),
                result.isBuffered(),
                result.getReadyMessages().size(),
                result.getReleasedMessages().size()
        );

        eventPublisher.publish(event);
    }
}
