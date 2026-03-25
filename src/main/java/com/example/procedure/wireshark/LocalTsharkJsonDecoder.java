package com.example.procedure.wireshark;

import com.example.procedure.infrastructure.decode.TsharkDecodeJsonTool;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * @deprecated 旧的本地 tshark JSON 解码器兼容层。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖这个类名
 * 2. 新的正式实现已经迁到 infrastructure.decode.TsharkDecodeJsonTool
 * 3. 这里收缩为兼容壳，避免旧调用方立即失效
 */
@Deprecated
@Component
public class LocalTsharkJsonDecoder implements PcapJsonDecoder {

    /**
     * 新的正式 decode 工具实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    private final TsharkDecodeJsonTool delegate;

    /**
     * 构造旧兼容层。
     *
     * 这里直接依赖正式实现类，
     * 避免把自己再次作为 DecodeJsonTool 候选注入进来。
     *
     * @param delegate 正式 decode 工具实现
     */
    public LocalTsharkJsonDecoder(TsharkDecodeJsonTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：一次性解码 JSON。
     *
     * @param pcapPath pcap 文件
     * @return JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public String decodeToJson(Path pcapPath) throws Exception {
        return delegate.decodeToJson(pcapPath);
    }

    /**
     * 兼容旧接口：流式解码 JSON。
     *
     * @param pcapPath pcap 文件
     * @param consumer 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public void decodeToJsonStream(Path pcapPath, Consumer<InputStream> consumer) throws Exception {
        delegate.decodeToJsonStream(pcapPath, consumer);
    }
}
