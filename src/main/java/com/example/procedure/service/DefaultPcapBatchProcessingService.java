package com.example.procedure.service;

import com.example.procedure.application.pcap.PcapBatchOrchestrator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;

/**
 * @deprecated 阶段 1 兼容层，请逐步改用 application.pcap.PcapBatchOrchestrator
 */
@Deprecated
@Service
@Primary
public class DefaultPcapBatchProcessingService implements PcapBatchProcessingService {

    private final PcapBatchOrchestrator delegate;

    public DefaultPcapBatchProcessingService(PcapBatchOrchestrator delegate) {
        this.delegate = delegate;
    }

    @Override
    public void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        delegate.process(pcap, wanted, enabledRaw);
    }
}