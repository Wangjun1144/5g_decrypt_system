package com.example.procedure.wireshark;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * pcap -> JSON 解码器。
 *
 * 当前用途：
 * 1. 为本地 tshark 进程调用建立正式基础设施接口
 * 2. 让上层不再依赖历史命名的 TsharkRunner
 * 3. 为后续替换成其他本地实现、远程代理实现、mock 实现预留稳定边界
 */
public interface PcapJsonDecoder {

    /**
     * 一次性把 pcap 解码成 JSON 字符串。
     *
     * @param pcapPath pcap 文件路径
     * @return 解码后的 JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    String decodeToJson(Path pcapPath) throws Exception;

    /**
     * 流式把 pcap 解码成 JSON 输出流。
     *
     * @param pcapPath pcap 文件路径
     * @param consumer JSON 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    void decodeToJsonStream(Path pcapPath, Consumer<InputStream> consumer) throws Exception;
}
