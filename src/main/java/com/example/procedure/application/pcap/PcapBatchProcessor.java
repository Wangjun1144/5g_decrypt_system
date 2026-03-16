package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Set;

/**
 * 统一的 pcap 批处理入口。
 *
 * 阶段 1 目标：
 * 1. 将“应用入口”从旧 service 包中剥离出来
 * 2. 为后续阶段 2 的 pipeline orchestrator 做准备
 * 3. 保持现有 process(...) 行为不变
 */
public interface PcapBatchProcessor {

    void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception;
}