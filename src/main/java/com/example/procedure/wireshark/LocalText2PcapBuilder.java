package com.example.procedure.wireshark;

import com.example.procedure.infrastructure.decode.Text2PcapBuildTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * @deprecated 旧的本地 text2pcap 构建器兼容层。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖这个类名
 * 2. 新的正式实现已经迁到 infrastructure.decode.Text2PcapBuildTool
 * 3. 这里收缩为兼容壳
 */
@Deprecated
@Component
public class LocalText2PcapBuilder implements PcapBuilder {

    /**
     * 新的正式 pcap 构建工具实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    private final Text2PcapBuildTool delegate;

    /**
     * 构造旧兼容层。
     *
     * 这里直接依赖正式实现类，
     * 避免把自己再次作为 PcapBuildTool 候选注入进来。
     *
     * @param delegate 正式 pcap 构建工具实现
     */
    public LocalText2PcapBuilder(Text2PcapBuildTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：根据 hexdump 构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 输出 pcap 文件路径
     * @throws Exception 构建失败时抛出异常
     */
    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        return delegate.buildPcap(hexdumpFile, dlt, outPcap);
    }
}
