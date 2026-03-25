package com.example.procedure.decodebridge;

import com.example.procedure.infrastructure.decode.TsharkDecodeJsonTool;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 基于正式基础设施 decode 工具的 pcap JSON 解码网关。
 *
 * 当前职责：
 * 1. 对 decodebridge 层暴露正式 JSON 解码 gateway
 * 2. 内部依赖 infrastructure.decode 包下的正式工具实现
 * 3. 不再依赖 wireshark 包里的历史命名接口
 */
@Service
public class TsharkJsonDecodeGateway implements PcapJsonDecodeGateway {

    /**
     * 底层 JSON 解码工具正式实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    private final TsharkDecodeJsonTool decoder;

    /**
     * 构造 JSON 解码网关。
     *
     * 这里直接依赖正式实现类，
     * 避免和旧兼容层 LocalTsharkJsonDecoder 形成 Spring 注入歧义。
     *
     * @param decoder 底层 JSON 解码工具正式实现
     */
    public TsharkJsonDecodeGateway(TsharkDecodeJsonTool decoder) {
        this.decoder = decoder;
    }

    /**
     * 一次性把 pcap 解码成 JSON。
     *
     * @param pcap pcap 文件
     * @return JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public String decodeToJson(Path pcap) throws Exception {
        Objects.requireNonNull(pcap, "pcap must not be null");
        return decoder.decodeToJson(pcap);
    }

    /**
     * 流式把 pcap 解码成 JSON。
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
