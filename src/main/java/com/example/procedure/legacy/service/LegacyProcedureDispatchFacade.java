package com.example.procedure.legacy.service;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.dispatch.ProcedureDispatchService;
import org.springframework.stereotype.Service;

/**
 * 旧流程分发入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 ProDispatcher_Service 的兼容职责
 * 2. 把旧分发命名和新的 dispatch 边界隔开
 * 3. 为后续删除旧 service 壳做准备
 */
@Service
public class LegacyProcedureDispatchFacade {

    /**
     * 新的流程分发服务。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final ProcedureDispatchService delegate;

    /**
     * 构造旧流程分发兼容 facade。
     *
     * @param delegate 新的流程分发服务
     */
    public LegacyProcedureDispatchFacade(ProcedureDispatchService delegate) {
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
