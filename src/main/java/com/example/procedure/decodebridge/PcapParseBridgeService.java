package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/**
 * pcap 解析 bridge 服务。
 *
 * 当前设计目标：
 * 1. 让上层通过 bridge 语义访问 pcap 解析能力
 * 2. 正式入口改为 PcapDecodeRequest
 * 3. 旧的多参数接口继续保留为兼容 default 方法
 */
public interface PcapParseBridgeService {

    /**
     * 正式入口：处理一个 pcap 解码请求。
     *
     * @param request pcap 解码请求
     * @throws Exception 解析失败时抛出异常
     */
    void parse(PcapDecodeRequest request) throws Exception;

    /**
     * 兼容旧接口：使用多参数方式发起 pcap 解析。
     *
     * 这个方法保留的原因是：
     * 1. 避免一次性修改过多旧调用方
     * 2. 让新旧调用方都统一落到新的正式入口
     *
     * @param pcap pcap 文件
     * @param wanted 需要保留的协议层
     * @param enabledRaw 需要启用 raw 输出的层
     * @param messageConsumer 下游消息消费者
     * @throws Exception 解析失败时抛出异常
     */
    default void parsePcap(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw,
            Consumer<SignalingMessage> messageConsumer
    ) throws Exception {
        parse(PcapDecodeRequest.of(pcap, wanted, enabledRaw, messageConsumer));
    }
}
