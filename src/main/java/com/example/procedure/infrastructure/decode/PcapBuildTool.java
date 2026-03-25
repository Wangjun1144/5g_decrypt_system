package com.example.procedure.infrastructure.decode;

import java.nio.file.Path;

/**
 * 底层 pcap 构建工具边界。
 *
 * 当前定位：
 * 1. 这是 infrastructure 层“hexdump -> pcap”的正式工具接口
 * 2. 上层不再直接依赖 text2pcap 这种具体工具命名
 * 3. 为后续替换成远程构建服务或 mock 实现预留稳定边界
 */
public interface PcapBuildTool {

    /**
     * 根据 hexdump 文件构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 输出 pcap 文件路径
     * @throws Exception 构建失败时抛出异常
     */
    Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception;
}
