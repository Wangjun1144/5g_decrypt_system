package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;

/**
 * “消息解密阶段”的统一入口接口。
 *
 * 设计目标：
 * 1. 把解密相关能力从主链中抽象为独立处理阶段。
 * 2. 让 MessageProcessor 不再直接依赖具体解密协调器实现。
 * 3. 为后续演进到异步解密、远程解密、事件驱动解密流水线做准备。
 *
 * 当前阶段的处理范围：
 * - 判断当前消息是否需要解密
 * - 执行当前轮解密尝试
 * - 在解密成功时执行回流
 *
 * 当前阶段不负责：
 * - pending 入队决策
 * - pending 重试调度
 * - 主链递归控制
 *
 * 这些决策仍然保留在 MessageProcessor 中，
 * 这样可以保证这一轮重构只收口边界，不改变主链控制语义。
 */
public interface MessageDecryptStage {

    /**
     * 对当前消息执行一次“如有需要则尝试解密”的阶段处理。
     *
     * 返回约定保持与当前系统一致：
     * - 返回 null：当前消息无需在此阶段提前结束，主链可继续
     * - 返回非 null：表示本轮已经发生了解密相关处理，主链需根据状态决定后续行为
     */
    DecryptAttemptResult handleEncryptedMessageIfNeeded(MessageProcessingContext context);

    /**
     * 在解密成功后执行回流处理。
     *
     * 返回值语义保持与当前实现一致：
     * - true  : 已发生有效回流，消息应重新进入完整主链
     * - false : 没有完成有效回流，主链可按当前上下文直接收口
     */
    boolean handleDecryptSuccess(MessageProcessingContext context);
}
