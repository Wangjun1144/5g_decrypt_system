package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Set;

/**
 * 新的 pcap 批处理应用入口。
 *
 * 设计意图：
 * 1. 这个接口代表“应用层视角”的统一处理入口
 * 2. 上层如果要触发一次完整的 pcap 批处理，应优先依赖这个接口
 * 3. 当前正式入口语义已收口为 PcapBatchProcessRequest
 *
 * 当前阶段定位：
 * - 它是第二阶段重构后的主入口接口
 * - 后续无论向流式处理、事件驱动、微服务拆分演进，都可以继续保留
 */
public interface PcapBatchProcessor {

    void process(PcapBatchProcessRequest request) throws Exception;

    /**
     * 兼容旧的三参数调用方式。
     */
    default void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        process(PcapBatchProcessRequest.of(pcap, wanted, enabledRaw));
    }
}
