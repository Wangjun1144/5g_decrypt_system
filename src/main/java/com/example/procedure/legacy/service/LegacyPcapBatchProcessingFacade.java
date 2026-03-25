package com.example.procedure.legacy.service;

import com.example.procedure.application.pcap.PcapBatchProcessRequest;
import com.example.procedure.application.pcap.PcapBatchProcessor;
import org.springframework.stereotype.Service;

/**
 * 旧 pcap 批处理入口兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 PcapBatchProcessingService 的兼容职责
 * 2. 把旧 service 命名和新的 application.pcap 入口隔开
 * 3. 为后续清理旧 service 包做准备
 */
@Service
public class LegacyPcapBatchProcessingFacade {

    /**
     * 新的 pcap 批处理入口。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG_PHASE2
    private final PcapBatchProcessor delegate;

    /**
     * 构造旧 pcap 批处理兼容 facade。
     *
     * @param delegate 新的 pcap 批处理入口
     */
    public LegacyPcapBatchProcessingFacade(PcapBatchProcessor delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：处理一个 pcap 批处理请求。
     *
     * @param request pcap 批处理请求
     * @throws Exception 处理失败时抛出异常
     */
    public void process(PcapBatchProcessRequest request) throws Exception {
        delegate.process(request);
    }
}
