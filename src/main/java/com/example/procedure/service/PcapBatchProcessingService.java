package com.example.procedure.service;

import java.nio.file.Path;
import java.util.Set;

/**
 * pcap 批处理服务
 *
 * 作用：
 * 1. 把“测试类中承担正式入口职责”的流程抽出来
 * 2. 以后无论是测试、命令行、还是接口调用，都复用这个入口
 */
public interface PcapBatchProcessingService {

    /**
     * 处理单个 pcap/pcapng 文件
     *
     * @param pcap       待处理抓包文件
     * @param wanted     需要从 tshark JSON 中提取的 layer
     * @param enabledRaw 需要严格配对消费的 *_raw layer
     */
    void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception;
}