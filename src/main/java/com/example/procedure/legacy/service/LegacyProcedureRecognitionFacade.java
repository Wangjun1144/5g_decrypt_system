package com.example.procedure.legacy.service;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.ProcedureRecognitionService;
import org.springframework.stereotype.Service;

/**
 * 旧流程识别入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 ProClassify_Service 的兼容职责
 * 2. 把旧命名和新的流程识别边界隔开
 * 3. 为后续清理 service 包做准备
 */
@Service
public class LegacyProcedureRecognitionFacade {

    /**
     * 新的流程识别服务。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final ProcedureRecognitionService delegate;

    /**
     * 构造旧流程识别兼容 facade。
     *
     * @param delegate 新的流程识别服务
     */
    public LegacyProcedureRecognitionFacade(ProcedureRecognitionService delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：处理一条消息的流程识别。
     *
     * @param msg 当前消息
     * @return 流程匹配结果
     */
    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        return delegate.recognize(msg);
    }
}
