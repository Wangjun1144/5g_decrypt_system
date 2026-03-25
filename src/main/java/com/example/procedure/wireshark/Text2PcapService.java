package com.example.procedure.wireshark;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * @deprecated 旧的 text2pcap 服务兼容层。
 *
 * 当前保留这个类的原因：
 * 1. 旧代码和旧测试可能还在依赖 Text2PcapService 这个名字
 * 2. 新主链已经迁移到 PcapBuilder / gateway 边界
 * 3. 这里的职责已经收缩为兼容门面
 */
@Deprecated
@Component
public class Text2PcapService {

    /**
     * 旧兼容层仍然依赖旧兼容接口，
     * 这样对老调用方最稳定。
     */
    private final PcapBuilder delegate;

    /**
     * 构造旧兼容层。
     *
     * @param delegate 旧兼容接口实现
     */
    public Text2PcapService(PcapBuilder delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：根据 hexdump 构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 构建完成后的 pcap 路径
     * @throws Exception 构建失败时抛出异常
     */
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        return delegate.buildPcap(hexdumpFile, dlt, outPcap);
    }
}
