package com.example.procedure.wireshark;

import java.nio.file.Path;

/**
 * pcap 构建器。
 *
 * 当前用途：
 * 1. 为本地 text2pcap 进程调用建立正式基础设施接口
 * 2. 让上层不再依赖历史命名的 Text2PcapService
 * 3. 为后续切换成本地替代实现、远程构建服务、mock 实现预留稳定边界
 */
public interface PcapBuilder {

    /**
     * 根据 hexdump 文件构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 构建完成后的 pcap 路径
     * @throws Exception 构建失败时抛出异常
     */
    Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception;
}
