package com.example.procedure.service;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的流程识别兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 ProClassify_Service
 * - 新主链已经迁移到 processing.procedure.ProcedureRecognitionService
 * - 为避免一次性修改过多调用方，这里保留旧类名作为兼容门面
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.processing.procedure.ProcedureRecognitionService
 * - 本类最终可迁入 legacy 包或删除
 */
@Deprecated
@Service
public class ProClassify_Service {

    private final com.example.procedure.processing.procedure.ProcedureRecognitionService delegate;

    public ProClassify_Service(
            com.example.procedure.processing.procedure.ProcedureRecognitionService delegate
    ) {
        this.delegate = delegate;
    }

    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        return delegate.recognize(msg);
    }
}
