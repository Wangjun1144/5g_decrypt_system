package com.example.procedure.processing.dispatch;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.service.ProDispatcher_Service;
import org.springframework.stereotype.Service;

/**
 * 阶段 1 兼容壳：
 * 标准化流程分发模块命名。
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