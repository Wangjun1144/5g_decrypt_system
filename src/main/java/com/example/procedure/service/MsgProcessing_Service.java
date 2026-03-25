package com.example.procedure.service;

import com.example.procedure.legacy.service.LegacyMessageProcessingFacade;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

/**
 * @deprecated 阶段 1 兼容层。
 *
 * 这是 legacy service 包中的旧门面类。
 *
 * 原因：
 * - 文档明确指出 MsgProcessing_Service 是当前主复杂度聚集点，
 *   应拆分为分类、解密、流程识别、分发、pending 重试等阶段服务。
 * - 为避免一次性修改过多调用方，当前先保留旧类名。
 * - 真正实现已经迁移到 processing.message.MessageProcessor
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 使用建议：
 * - 新代码直接注入 MessageProcessor
 * - 旧代码暂时仍可注入 MsgProcessing_Service，不影响功能
 */
@Deprecated
@Service
public class MsgProcessing_Service {

    /**
     * 旧消息处理兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final LegacyMessageProcessingFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧消息处理兼容 facade
     */
    public MsgProcessing_Service(LegacyMessageProcessingFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧调用入口。
     *
     * @param msg 当前消息
     * @return 处理结果
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        return delegate.process(msg);
    }
}
