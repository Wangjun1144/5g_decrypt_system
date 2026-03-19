package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.MessageBindingProcessor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 旧的 UE 绑定兼容门面。
 *
 * 当前阶段保留这个类的目的：
 * 1. 避免一次性改动所有旧代码和旧测试
 * 2. 让历史命名在迁移阶段继续可用
 * 3. 把真正的绑定处理逐步转移到 processing.binding 包中的新入口
 *
 * 这一步之后，这个类的角色会更加清晰：
 * - 它不再代表推荐使用的绑定入口
 * - 它只是旧调用方式的兼容壳
 *
 * 新代码使用建议：
 * - 不要继续优先依赖 UeIdBinder
 * - 优先依赖 MessageBindingProcessor 或 UeBindingService
 */
@Deprecated
@Service
public class UeIdBinder {

    private final MessageBindingProcessor bindingProcessor;

    public UeIdBinder(MessageBindingProcessor bindingProcessor) {
        this.bindingProcessor = bindingProcessor;
    }

    /**
     * 兼容旧接口：
     * 输入一条消息，若可继续处理，则通过 downstream 输出。
     *
     * 当前实现不再直接承载绑定核心逻辑，
     * 而是透明转发到新的绑定阶段入口。
     */
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        bindingProcessor.handle(msg, downstream);
    }
}
