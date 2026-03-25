package com.example.procedure.decodebridge;

import com.example.procedure.infrastructure.decode.Text2PcapBuildTool;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 基于正式基础设施构建工具的 pcap 构建网关。
 *
 * 当前职责：
 * 1. 对 decodebridge 层暴露正式 pcap 构建 gateway
 * 2. 内部依赖 infrastructure.decode 包下的正式工具实现
 * 3. 不再依赖 wireshark 包里的历史命名接口
 */
@Service
public class Text2PcapBuildGateway implements PcapBuildGateway {

    /**
     * 底层 pcap 构建工具正式实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    private final Text2PcapBuildTool builder;

    /**
     * 构造 pcap 构建网关。
     *
     * 这里直接依赖正式实现类，
     * 避免和旧兼容层 LocalText2PcapBuilder 形成 Spring 注入歧义。
     *
     * @param builder 底层 pcap 构建工具正式实现
     */
    public Text2PcapBuildGateway(Text2PcapBuildTool builder) {
        this.builder = builder;
    }

    /**
     * 根据 hexdump 文件构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 输出 pcap 文件路径
     * @throws Exception 构建失败时抛出异常
     */
    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        Objects.requireNonNull(hexdumpFile, "hexdumpFile must not be null");
        Objects.requireNonNull(outPcap, "outPcap must not be null");
        return builder.buildPcap(hexdumpFile, dlt, outPcap);
    }
}
