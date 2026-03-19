package com.example.procedure.processing.procedure;

import com.example.procedure.processing.message.MessageProcessingContext;

/**
 * “流程处理阶段”的统一入口接口。
 *
 * 第 10 小步后的设计变化：
 * - 不再只接收 msg + category
 * - 改为直接接收主链共享上下文 MessageProcessingContext
 *
 * 这样做的原因：
 * 1. 流程阶段开始真正与主链上下文对齐
 * 2. 后续如果流程阶段需要读取更多处理中间状态，不必频繁改方法签名
 * 3. 更符合“主链阶段围绕 context 协作”的重构方向
 *
 * 当前阶段职责：
 * - 判断当前消息是否需要进入流程识别
 * - 如需要，则执行流程识别
 * - 执行统一分发
 * - 将流程识别结果写回上下文
 *
 * 当前阶段约束：
 * - 不改变现有流程识别规则
 * - 不改变现有流程分发逻辑
 * - 不承担 UEContext 更新和 pending 解密重试
 */
public interface ProcedureProcessingStage {

    void process(MessageProcessingContext context);
}
