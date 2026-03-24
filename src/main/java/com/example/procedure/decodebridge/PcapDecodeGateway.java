package com.example.procedure.decodebridge;

/**
 * pcap 解码能力访问边界。
 *
 * 当前用途：
 * 1. 把“如何从 pcap 解出 SignalingMessage”从上层编排逻辑中抽离出来
 * 2. 让 decodebridge 层只依赖正式 gateway，而不直接依赖 tshark 细节
 * 3. 为后续切换到独立解码服务、本地 mock、异步解码 worker 预留稳定边界
 */
public interface PcapDecodeGateway {

    /**
     * 执行一次 pcap 解码。
     *
     * 当前语义很直接：
     * - 输入一个正式的解码请求
     * - 在解析过程中持续把消息推给下游消费者
     *
     * @param request pcap 解码请求
     * @throws Exception 解码或解析失败时抛出异常
     */
    void decode(PcapDecodeRequest request) throws Exception;
}
