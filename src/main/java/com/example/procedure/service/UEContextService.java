package com.example.procedure.service;

import com.example.procedure.context.UeContextUpdateRequest;
import com.example.procedure.context.UeContextUpdateResult;
import com.example.procedure.legacy.service.LegacyUeContextFacade;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的 UE 上下文服务兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 service.UEContextService
 * - 新主链已经统一迁移到 context.UeContextService
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.context.UeContextService
 * - 本类最终可迁入真正的 legacy 包或删除
 */
@Deprecated
@Service
public class UEContextService {

    /**
     * 旧 UEContext 兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final LegacyUeContextFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧 UEContext 兼容 facade
     */
    public UEContextService(LegacyUeContextFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：加载上下文。
     *
     * @param ueId UE 标识
     * @return UEContext
     */
    public UEContext getContext(String ueId) {
        return delegate.getContext(ueId);
    }

    /**
     * 兼容旧接口：按旧签名更新上下文。
     *
     * @param msg 当前消息
     * @param procedureId 当前流程 ID
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
     * 兼容旧接口：保存上下文。
     *
     * @param context 当前上下文
     */
    public void saveContext(UEContext context) {
        delegate.saveContext(context);
    }

    /**
     * 兼容旧接口：不存在则创建默认上下文。
     *
     * @param ueId UE 标识
     * @return UEContext
     */
    public UEContext getOrCreate(String ueId) {
        return delegate.getOrCreate(ueId);
    }
}
