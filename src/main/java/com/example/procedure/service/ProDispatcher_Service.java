package com.example.procedure.service;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的流程分发兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码或旧测试可能仍然依赖 ProDispatcher_Service
 * - 新主链已经迁移到 processing.dispatch.ProcedureDispatchService
 * - 为避免一次性修改所有旧调用方，这里保留旧类名作为兼容门面
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.processing.dispatch.ProcedureDispatchService
 * - 本类最终可迁入 legacy 包或删除
 */
@Deprecated
@Service
public class ProDispatcher_Service {

    private final com.example.procedure.processing.dispatch.ProcedureDispatchService delegate;

    public ProDispatcher_Service(
            com.example.procedure.processing.dispatch.ProcedureDispatchService delegate
    ) {
        this.delegate = delegate;
    }

    public void dispatch(
            SignalingMessage msg,
            MessageCategory category,
            String procedureId,
            String procedureType
    ) {
        delegate.dispatch(msg, category, procedureId, procedureType);
    }
}
