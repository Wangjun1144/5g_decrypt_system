package com.example.procedure.decodebridge;

import java.nio.file.Path;

/**
 * pcap 构建能力访问边界。
 *
 * 当前用途：
 * 1. 把“如何把 hexdump 转成 pcap”从上层逻辑中抽离出来
 * 2. 让 decodebridge 不再直接依赖 text2pcap 进程调用细节
 * 3. 为后续切换本地 mock、远程构建服务、异步构建 worker 预留稳定边界
 */
public interface PcapBuildGateway {

    /**
     * 根据 hexdump 文件构建 pcap 文件。
     *
     * 当前语义很直接：
     * - 输入 hexdump 文件、目标 DLT、输出 pcap 路径
     * - 返回构建完成后的 pcap 路径
     *
     * @param hexdumpFile hexdump 文本文件
     * @param dlt 数据链路层类型
     * @param outPcap 输出 pcap 文件
     * @return 构建完成后的 pcap 文件路径
     * @throws Exception 构建失败时抛出异常
     */
    Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception;
}
