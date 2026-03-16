package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;

/**
 * UE 上下文更新器
 *
 * 每个 updater 只负责一种消息类型的上下文更新逻辑。
 * 这样可以替代 UEContextService.updateOnInitialAccess(...) 中的大量 if-else。
 */
public interface UeContextUpdater {

    /**
     * 当前 updater 是否支持处理这条消息
     */
    boolean supports(SignalingMessage msg);

    /**
     * 执行上下文更新
     *
     * @param msg         当前信令消息
     * @param ctx         当前 UE 上下文（已存在或刚创建）
     * @param procedureId 当前流程实例 ID
     * @param support     上下文更新辅助能力（映射写入、密钥推导、工具方法）
     */
    void update(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support);
}
