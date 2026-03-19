package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 绑定阶段的新默认实现。
 *
 * 当前阶段的角色非常明确：
 * 1. 作为“新绑定入口”的默认实现
 * 2. 内部继续复用已经拆出的 BindingResolver
 * 3. 不改变当前绑定规则，只把入口和职责边界进一步明确
 *
 * 为什么这一步值得单独做：
 * - 现在 BindingResolver 已经承接了原 UeIdBinder 的大部分核心逻辑
 * - 但系统对外的新依赖边界还不够明确
 * - 增加这一层后，domain/application 层可以只依赖 MessageBindingProcessor
 *
 * 对未来流式分布式演进的意义：
 * - 现在先把“绑定”抽成稳定处理阶段
 * - 后续才能自然演化到：
 *   绑定 topic / 绑定 worker / 状态外置 / 幂等消费 / 事件回放
 *
 * 当前行为保持不变：
 * - 若结果是 buffered，则不调用 downstream
 * - 若结果可继续处理，则先下发 released pending，再下发当前消息
 */
@Service
public class DefaultMessageBindingProcessor implements MessageBindingProcessor {

    private final BindingResolver bindingResolver;

    public DefaultMessageBindingProcessor(BindingResolver bindingResolver) {
        this.bindingResolver = bindingResolver;
    }

    @Override
    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        BindingResolutionResult result = bindingResolver.resolve(msg);

        // 当前消息如果被缓冲，说明本轮尚不能进入主处理链。
        if (result.isBuffered()) {
            return;
        }

        // 保持旧系统的重要顺序语义：
        // 先释放历史 pending，再处理当前消息。
        for (SignalingMessage readyMsg : result.toDownstreamOrder()) {
            downstream.accept(readyMsg);
        }
    }
}
