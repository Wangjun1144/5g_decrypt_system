package com.example.procedure.service;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.MessageProcessor;
import org.springframework.stereotype.Service;

/**
 * @deprecated 阶段 1 兼容层。
 *
 * 原因：
 * - 文档明确指出 MsgProcessing_Service 是当前主复杂度聚集点，
 *   应拆分为分类、解密、流程识别、分发、pending 重试等阶段服务。
 * - 为避免一次性修改过多调用方，当前先保留旧类名
 * - 真正实现已经迁移到 processing.message.MessageProcessor
 *
 * 使用建议：
 * - 新代码直接注入 MessageProcessor
 * - 旧代码暂时仍可注入 MsgProcessing_Service，不影响功能
 */
@Deprecated
@Service
public class MsgProcessing_Service {

    private final MessageProcessor delegate;

    public MsgProcessing_Service(MessageProcessor delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧调用入口。
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        return delegate.process(msg);
    }
}