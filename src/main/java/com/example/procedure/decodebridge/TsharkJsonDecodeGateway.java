package com.example.procedure.decodebridge;

import com.example.procedure.wireshark.PcapJsonDecoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 基于本地 JSON 解码器的 pcap JSON 解码网关。
 *
 * 当前职责：
 * 1. 对 decodebridge 层暴露正式 JSON 解码 gateway
 * 2. 内部依赖更底层的 PcapJsonDecoder
 * 3. 不再依赖历史命名的 TsharkRunner
 */
@Service
public class TsharkJsonDecodeGateway implements PcapJsonDecodeGateway {

    /**
     * 底层 pcap JSON 解码器。
     */
    private final PcapJsonDecoder decoder;

    /**
     * 构造 JSON 解码网关。
     *
     * @param decoder 底层 pcap JSON 解码器
     */
    public TsharkJsonDecodeGateway(PcapJsonDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * 一次性把 pcap 解码成 JSON 字符串。
     *
     * @param pcap pcap 文件
     * @return 解码后的 JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public String decodeToJson(Path pcap) throws Exception {
        Objects.requireNonNull(pcap, "pcap must not be null");
        return decoder.decodeToJson(pcap);
    }

    /**
     * 流式把 pcap 解码成 JSON 输出流。
     *
     * @param pcap pcap 文件
     * @param jsonConsumer JSON 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public void decodeToJsonStream(Path pcap, Consumer<InputStream> jsonConsumer) throws Exception {
        Objects.requireNonNull(pcap, "pcap must not be null");
        Objects.requireNonNull(jsonConsumer, "jsonConsumer must not be null");
        decoder.decodeToJsonStream(pcap, jsonConsumer);
    }
}
