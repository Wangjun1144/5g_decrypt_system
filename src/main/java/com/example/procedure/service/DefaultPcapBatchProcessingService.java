package com.example.procedure.service;

import com.example.procedure.application.pcap.PcapBatchProcessRequest;
import com.example.procedure.legacy.service.LegacyPcapBatchProcessingFacade;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 旧入口接口的默认兼容适配实现。
 *
 * 它本质上不再承担真正的业务处理职责，
 * 只负责把仍然依赖旧接口的调用平滑转发到 legacy facade，
 * 再由 facade 进入新的 application 层入口。
 */
@Deprecated
@Service
@Primary
public class DefaultPcapBatchProcessingService implements PcapBatchProcessingService {

    /**
     * 旧 pcap 批处理兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final LegacyPcapBatchProcessingFacade delegate;

    /**
     * 构造旧默认适配实现。
     *
     * @param delegate 旧 pcap 批处理兼容 facade
     */
    public DefaultPcapBatchProcessingService(LegacyPcapBatchProcessingFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：处理一个 pcap 批处理请求。
     *
     * @param request pcap 批处理请求
     * @throws Exception 处理失败时抛出异常
     */
    @Override
    public void process(PcapBatchProcessRequest request) throws Exception {
        delegate.process(request);
    }
}
