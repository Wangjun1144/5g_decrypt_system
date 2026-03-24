package com.example.procedure.service;

import com.example.procedure.application.pcap.PcapBatchProcessRequest;

import java.nio.file.Path;
import java.util.Set;

/**
 * @deprecated 旧的 pcap 批处理兼容接口。
 *
 * 使用建议：
 * - 新代码不要继续优先依赖这个接口
 * - 新代码应优先依赖 application.pcap 包下的 PcapBatchProcessor
 * - 这个接口未来的角色应逐步收缩为“兼容旧调用方”的过渡层
 */
@Deprecated
public interface PcapBatchProcessingService {

    void process(PcapBatchProcessRequest request) throws Exception;

    default void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        process(PcapBatchProcessRequest.of(pcap, wanted, enabledRaw));
    }
}
