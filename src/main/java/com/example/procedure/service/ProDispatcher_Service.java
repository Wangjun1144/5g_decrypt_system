package com.example.procedure.service;

import com.example.procedure.legacy.service.LegacyProcedureDispatchFacade;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的流程分发兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码或旧测试可能仍然依赖 ProDispatcher_Service
 * - 新主链已经迁移到 processing.dispatch.ProcedureDispatchService
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.processing.dispatch.ProcedureDispatchService
 * - 本类最终可迁入真正的 legacy 包或删除
 */
@Deprecated
@Service
public class ProDispatcher_Service {

    /**
     * 旧流程分发兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final LegacyProcedureDispatchFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧流程分发兼容 facade
     */
    public ProDispatcher_Service(LegacyProcedureDispatchFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：执行流程分发。
     *
     * @param msg 当前消息
     * @param category 当前分类
     * @param procedureId 流程 ID
     * @param procedureType 流程类型
     */
    public void dispatch(
            SignalingMessage msg,
            MessageCategory category,
            String procedureId,
            String procedureType
    ) {
        delegate.dispatch(msg, category, procedureId, procedureType);
    }
}
