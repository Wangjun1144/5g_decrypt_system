package com.example.procedure.decodebridge;

import com.example.procedure.wireshark.PcapBuilder;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 基于底层 pcap 构建器的 pcap 构建网关。
 *
 * 当前职责：
 * 1. 对 decodebridge 层暴露正式 pcap 构建 gateway
 * 2. 内部依赖更底层的 PcapBuilder
 * 3. 不再依赖历史命名的 Text2PcapService
 */
@Service
public class Text2PcapBuildGateway implements PcapBuildGateway {

    /**
     * 底层 pcap 构建器。
     */
    private final PcapBuilder builder;

    /**
     * 构造 pcap 构建网关。
     *
     * @param builder 底层 pcap 构建器
     */
    public Text2PcapBuildGateway(PcapBuilder builder) {
        this.builder = builder;
    }

    /**
     * 根据 hexdump 文件构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 构建完成后的 pcap 路径
     * @throws Exception 构建失败时抛出异常
     */
    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        Objects.requireNonNull(hexdumpFile, "hexdumpFile must not be null");
        Objects.requireNonNull(outPcap, "outPcap must not be null");
        return builder.buildPcap(hexdumpFile, dlt, outPcap);
    }
}
