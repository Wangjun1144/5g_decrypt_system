package com.example.procedure.wireshark;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * @deprecated 旧的 tshark 运行器兼容层。
 *
 * 当前保留这个类的原因：
 * 1. 旧代码和旧测试可能还在依赖 TsharkRunner 这个名字
 * 2. 新主链已经迁移到 PcapJsonDecoder / gateway 边界
 * 3. 这里的职责已经收缩为兼容门面
 */
@Deprecated
@Component
public class TsharkRunner {

    /**
     * 旧兼容层仍然依赖旧兼容接口，
     * 这样对老调用方最稳定。
     */
    private final PcapJsonDecoder delegate;

    /**
     * 构造旧兼容层。
     *
     * @param delegate 正式兼容接口实现
     */
    public TsharkRunner(PcapJsonDecoder delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：一次性解码 JSON。
     *
     * @param pcapPath pcap 文件
     * @return 解码后的 JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    public String decodeToJson(Path pcapPath) throws Exception {
        return delegate.decodeToJson(pcapPath);
    }

    /**
     * 兼容旧接口：流式解码 JSON。
     *
     * @param pcapPath pcap 文件
     * @param consumer JSON 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    public void decodeToJsonStream(Path pcapPath, Consumer<InputStream> consumer) throws Exception {
        delegate.decodeToJsonStream(pcapPath, consumer);
    }
}
