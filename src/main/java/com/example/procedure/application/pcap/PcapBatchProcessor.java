package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Set;

/**
 * 新的 pcap 批处理应用入口。
 *
 * 设计意图：
 * 1. 这个接口代表“应用层视角”的统一处理入口。
 * 2. 上层如果要触发一次完整的 pcap 批处理，应该优先依赖这个接口，
 *    而不是继续直接依赖旧的 service 包接口。
 * 3. 这一层只表达“处理一批 pcap 数据”这件事，不承载具体的解析、绑定、
 *    解密、流程识别等细节。
 *
 * 当前阶段的定位：
 * - 它是第二阶段重构后的主入口接口。
 * - 后续如果要向流式处理、事件驱动、微服务拆分演进，
 *   这一层可以继续稳定存在，作为应用编排边界。
 *
 * 参数说明：
 * @param pcap       待处理的 pcap/pcapng 文件路径
 * @param wanted     需要保留和解析的协议层集合
 * @param enabledRaw 需要额外保留原始 raw 信息的层集合
 */
public interface PcapBatchProcessor {

    void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception;
}
