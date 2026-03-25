package com.example.procedure.legacy.service;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.MessageProcessor;
import org.springframework.stereotype.Service;

/**
 * 旧消息处理入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 MsgProcessing_Service 的兼容职责
 * 2. 把旧命名和新主链 MessageProcessor 隔开
 * 3. 为后续删除旧 service 包包装壳做准备
 */
@Service
public class LegacyMessageProcessingFacade {

    /**
     * 新的消息主处理器。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final MessageProcessor delegate;

    /**
     * 构造旧消息处理兼容 facade。
     *
     * @param delegate 新的消息主处理器
     */
    public LegacyMessageProcessingFacade(MessageProcessor delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：处理一条消息。
     *
     * @param msg 当前消息
     * @return 处理结果
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        return delegate.process(msg);
    }
}
