package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * pcap 解码请求对象。
 *
 * 当前用途：
 * 1. 统一承接“一个 pcap 如何被解析成 SignalingMessage”的入口参数
 * 2. 让 decodebridge 层不再依赖一串松散的位置参数
 * 3. 为后续补充 traceId、sourceName、decodeProfile 等元数据预留位置
 */
public class PcapDecodeRequest {

    /**
     * 当前要解析的 pcap 文件。
     */
    private final Path pcap;

    /**
     * 希望保留的协议层集合。
     */
    private final Set<String> wanted;

    /**
     * 需要额外启用 raw 输出的层集合。
     */
    private final Set<String> enabledRaw;

    /**
     * 每解析出一条消息后的下游消费者。
     */
    private final Consumer<SignalingMessage> messageConsumer;

    /**
     * 构造 pcap 解码请求。
     *
     * @param pcap 当前 pcap 文件
     * @param wanted 需要保留的协议层集合
     * @param enabledRaw 需要启用 raw 输出的层集合
     * @param messageConsumer 下游消息消费者
     */
    public PcapDecodeRequest(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw,
            Consumer<SignalingMessage> messageConsumer
    ) {
        this.pcap = pcap;
        this.wanted = immutableCopy(wanted);
        this.enabledRaw = immutableCopy(enabledRaw);
        this.messageConsumer = messageConsumer;
    }

    /**
     * 工厂方法：创建一个 pcap 解码请求。
     *
     * @param pcap 当前 pcap 文件
     * @param wanted 需要保留的协议层集合
     * @param enabledRaw 需要启用 raw 输出的层集合
     * @param messageConsumer 下游消息消费者
     * @return 新的 pcap 解码请求
     */
    public static PcapDecodeRequest of(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw,
            Consumer<SignalingMessage> messageConsumer
    ) {
        return new PcapDecodeRequest(pcap, wanted, enabledRaw, messageConsumer);
    }

    /**
     * 获取当前 pcap 文件。
     *
     * @return 当前 pcap 文件
     */
    public Path getPcap() {
        return pcap;
    }

    /**
     * 获取 wanted 集合。
     *
     * @return wanted 集合
     */
    public Set<String> getWanted() {
        return wanted;
    }

    /**
     * 获取 enabledRaw 集合。
     *
     * @return enabledRaw 集合
     */
    public Set<String> getEnabledRaw() {
        return enabledRaw;
    }

    /**
     * 获取下游消息消费者。
     *
     * @return 下游消息消费者
     */
    public Consumer<SignalingMessage> getMessageConsumer() {
        return messageConsumer;
    }

    /**
     * 把输入集合拷贝成不可变集合。
     *
     * 这样做的意义是：
     * 1. 避免调用方在请求创建后继续修改集合
     * 2. 保证解码阶段看到的输入是稳定的
     *
     * @param source 原始集合
     * @return 不可变副本
     */
    private static Set<String> immutableCopy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
