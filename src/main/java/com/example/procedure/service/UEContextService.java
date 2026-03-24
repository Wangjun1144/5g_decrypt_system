package com.example.procedure.service;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的 UE 上下文服务兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 service.UEContextService
 * - 新主链已经统一迁移到 context.UeContextService
 * - 为避免一次性修改过多调用方，这里保留旧类名作为门面
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.context.UeContextService
 * - 本类最终可迁入 legacy 包或删除
 */
@Deprecated
@Service
public class UEContextService {

    private final com.example.procedure.context.UeContextService delegate;

    public UEContextService(com.example.procedure.context.UeContextService delegate) {
        this.delegate = delegate;
    }

    public UEContext getContext(String ueId) {
        return delegate.getContext(ueId);
    }

    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        delegate.updateOnInitialAccess(msg, procedureId);
    }

    public void saveContext(UEContext context) {
        delegate.saveContext(context);
    }

    public UEContext getOrCreate(String ueId) {
        return delegate.getOrCreate(ueId);
    }
}
