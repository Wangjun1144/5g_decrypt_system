package com.example.procedure.legacy.service;

import com.example.procedure.context.UeContextUpdateRequest;
import com.example.procedure.context.UeContextUpdateResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * 旧 UEContext 入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 service.UEContextService 的兼容职责
 * 2. 把旧命名和新的 context.UeContextService 隔开
 * 3. 为后续继续清理旧 service 包做准备
 */
@Service
public class LegacyUeContextFacade {

    /**
     * 新的正式 UEContext 服务。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final com.example.procedure.context.UeContextService delegate;

    /**
     * 构造旧 UEContext 兼容 facade。
     *
     * @param delegate 新的正式 UEContext 服务
     */
    public LegacyUeContextFacade(com.example.procedure.context.UeContextService delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：加载上下文。
     *
     * @param ueId UE 标识
     * @return UEContext
     */
    public UEContext getContext(String ueId) {
        return delegate.getContext(ueId);
    }

    /**
     * 兼容旧入口：按旧签名更新上下文。
     *
     * @param msg 当前消息
     * @param procedureId 流程 ID
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        delegate.updateOnInitialAccess(msg, procedureId);
    }

    /**
     * 正式兼容入口：处理一个上下文更新请求。
     *
     * @param request 更新请求
     * @return 更新结果
     */
    public UeContextUpdateResult process(UeContextUpdateRequest request) {
        return delegate.process(request);
    }

    /**
     * 兼容旧入口：保存上下文。
     *
     * @param context 当前上下文
     */
    public void saveContext(UEContext context) {
        delegate.saveContext(context);
    }

    /**
     * 兼容旧入口：不存在则创建默认上下文。
     *
     * @param ueId UE 标识
     * @return UEContext
     */
    public UEContext getOrCreate(String ueId) {
        return delegate.getOrCreate(ueId);
    }
}
