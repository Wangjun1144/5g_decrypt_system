package com.example.procedure.domain.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.MessageBindingProcessor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 领域层视角下的 UE 绑定服务。
 *
 * 这一层的职责是：
 * 1. 给上层编排逻辑提供一个更易理解的业务命名
 * 2. 隐藏底层具体绑定实现细节
 * 3. 让 application 层依赖“绑定阶段能力”，而不是历史兼容类名
 *
 * 当前阶段的重要调整：
 * - 旧版本这里直接依赖 UeIdBinder
 * - 现在改为依赖新的 MessageBindingProcessor
 *
 * 这样做的意义：
 * - 新主链路不再被旧兼容类名绑住
 * - UeIdBinder 可以逐步退化为纯兼容门面
 * - 后续如果绑定阶段要换成新的实现，这里不需要再改调用方
 */
@Service
public class UeBindingService {

    private final MessageBindingProcessor bindingProcessor;

    public UeBindingService(MessageBindingProcessor bindingProcessor) {
        this.bindingProcessor = bindingProcessor;
    }

    /**
     * 对一条消息执行 UE 绑定阶段处理。
     *
     * 说明：
     * - 如果当前消息尚不能确定 ueId，可能被缓冲，不会立即进入 downstream
     * - 如果当前消息已经可以继续处理，则会按绑定阶段既定顺序输出
     */
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        bindingProcessor.handle(msg, downstream);
    }
}
