package com.example.procedure.application.pcap;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * pcap 批处理请求。
 *
 * 当前用途：
 * - 统一承接 pcap 批处理入口参数
 * - 让应用层入口不再依赖松散的位置参数
 *
 * 后续演进：
 * - 可继续补充 traceId、sourceName、decodeProfile、decryptEnabled 等字段
 * - 可直接作为 API 入参模型、任务模型或事件负载模型
 */
public class PcapBatchProcessRequest {

    private final Path pcap;
    private final Set<String> wanted;
    private final Set<String> enabledRaw;

    public PcapBatchProcessRequest(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw
    ) {
        this.pcap = pcap;
        this.wanted = immutableCopy(wanted);
        this.enabledRaw = immutableCopy(enabledRaw);
    }

    public static PcapBatchProcessRequest of(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw
    ) {
        return new PcapBatchProcessRequest(pcap, wanted, enabledRaw);
    }

    public Path getPcap() {
        return pcap;
    }

    public Set<String> getWanted() {
        return wanted;
    }

    public Set<String> getEnabledRaw() {
        return enabledRaw;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
