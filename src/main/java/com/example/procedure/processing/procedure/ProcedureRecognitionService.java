package com.example.procedure.processing.procedure;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.service.ProClassify_Service;
import org.springframework.stereotype.Service;

/**
 * 阶段 1 兼容壳：
 * 标准化流程识别模块命名。
 */
@Service
public class ProcedureRecognitionService {

    private final ProClassify_Service delegate;

    public ProcedureRecognitionService(ProClassify_Service delegate) {
        this.delegate = delegate;
    }

    public ProcedureMatchResult recognize(SignalingMessage msg) {
        return delegate.handleMessage(msg);
    }
}