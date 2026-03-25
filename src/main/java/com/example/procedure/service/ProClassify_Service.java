package com.example.procedure.service;

import com.example.procedure.legacy.service.LegacyProcedureRecognitionFacade;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * @deprecated 旧的流程识别兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 ProClassify_Service
 * - 新主链已经迁移到 processing.procedure.ProcedureRecognitionService
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 后续建议：
 * - 新代码只依赖 com.example.procedure.processing.procedure.ProcedureRecognitionService
 * - 本类最终可迁入真正的 legacy 包或删除
 */
@Deprecated
@Service
public class ProClassify_Service {

    /**
     * 旧流程识别兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final LegacyProcedureRecognitionFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧流程识别兼容 facade
     */
    public ProClassify_Service(LegacyProcedureRecognitionFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：识别当前消息对应流程。
     *
     * @param msg 当前消息
     * @return 流程匹配结果
     */
    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        return delegate.handleMessage(msg);
    }
}
