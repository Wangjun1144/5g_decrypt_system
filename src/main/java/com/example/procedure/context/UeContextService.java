package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * 阶段 1：
 * 先统一命名风格，避免 UEContextService / UeContext... 混用。
 *
 * 当前只是对旧实现做包装，后续阶段再迁移实现。
 */
@Service
public class UeContextService {

    private final com.example.procedure.service.UEContextService delegate;

    public UeContextService(com.example.procedure.service.UEContextService delegate) {
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
}