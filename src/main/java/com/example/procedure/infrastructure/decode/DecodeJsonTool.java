package com.example.procedure.infrastructure.decode;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 底层 pcap -> JSON 解码工具边界。
 *
 * 当前定位：
 * 1. 这是最底层的本地/远程解码工具抽象
 * 2. 上层 bridge/gateway 只关心“给我 JSON”，不关心是 tshark 还是别的工具
 * 3. 这是 infrastructure 层的正式入口之一
 */
public interface DecodeJsonTool {

    /**
     * 一次性把 pcap 解码成 JSON 字符串。
     *
     * @param pcapPath pcap 文件路径
     * @return JSON 字符串
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
