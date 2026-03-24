package com.example.procedure.service;

import com.example.procedure.application.pcap.PcapBatchProcessRequest;
import com.example.procedure.application.pcap.PcapBatchProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 旧入口接口的默认兼容适配实现。
 *
 * 它本质上不再承担真正的业务处理职责，
 * 只负责把仍然依赖旧接口的调用平滑转发到新的应用层入口。
 */
@Deprecated
@Service
@Primary
public class DefaultPcapBatchProcessingService implements PcapBatchProcessingService {

    private final PcapBatchProcessor delegate;

    public DefaultPcapBatchProcessingService(PcapBatchProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void process(PcapBatchProcessRequest request) throws Exception {
        delegate.process(request);
    }
}
