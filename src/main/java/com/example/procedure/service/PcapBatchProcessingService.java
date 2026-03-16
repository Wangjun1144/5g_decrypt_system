package com.example.procedure.service;

import java.nio.file.Path;
import java.util.Set;

/**
 * @deprecated 阶段 1 兼容接口，后续请迁移到 application.pcap.PcapBatchProcessor
 */
@Deprecated
public interface PcapBatchProcessingService {

    void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception;
}