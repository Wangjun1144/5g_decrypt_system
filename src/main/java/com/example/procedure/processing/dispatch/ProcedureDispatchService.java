package com.example.procedure.processing.dispatch;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.service.ProDispatcher_Service;
import org.springframework.stereotype.Service;

/**
 * 流程分发服务。
 *
 * 当前阶段定位：
 * - 这是“流程处理阶段”中的分发组件
 * - 它负责把流程相关分发动作委托给现有实现
 * - 这一层当前主要承担稳定边界和统一命名职责
 *
 * 为什么这层先保留：
 * - 当前项目仍在渐进重构
 * - 旧分发逻辑还没有完全迁移出来
 * - 直接改写风险较高，不适合小步推进
 *
 * 后续演进方向：
 * - 可以继续把分发策略逐步迁到 processing/dispatch 内部
 * - 也可以在未来对接消息总线、事件发布器或分布式下游服务
 */
@Service
public class ProcedureDispatchService {

    private final ProDispatcher_Service delegate;

    public ProcedureDispatchService(ProDispatcher_Service delegate) {
        this.delegate = delegate;
    }

    public void dispatch(
            SignalingMessage msg,
            MessageCategory category,
            String procedureId,
            String procedureTypeCode
    ) {
        delegate.dispatch(msg, category, procedureId, procedureTypeCode);
    }
}
