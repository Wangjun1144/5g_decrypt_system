package com.example.procedure.decodebridge;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * pcap -> JSON 解码能力访问边界。
 *
 * 当前用途：
 * 1. 把“如何调用 tshark 生成 JSON”从上层逻辑中抽离出来
 * 2. 让 bridge 和应用层不再直接依赖 TsharkRunner
 * 3. 为后续替换成远程解码服务或其他实现预留正式接口
 */
public interface PcapJsonDecodeGateway {

    /**
     * 把 pcap 一次性解码成 JSON 字符串。
     *
     * @param pcap pcap 文件
     * @return 解码后的 JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    String decodeToJson(Path pcap) throws Exception;

    /**
     * 把 pcap 流式解码成 JSON 输出流。
     *
     * 调用方可以在 consumer 中直接消费 tshark 的 stdout，
     * 避免把超大 JSON 全量读入内存。
     *
     * @param pcap pcap 文件
     * @param jsonConsumer JSON 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    void decodeToJsonStream(Path pcap, Consumer<InputStream> jsonConsumer) throws Exception;
}
