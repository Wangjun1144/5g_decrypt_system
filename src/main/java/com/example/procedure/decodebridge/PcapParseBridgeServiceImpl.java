package com.example.procedure.decodebridge;

import org.springframework.stereotype.Service;

/**
 * pcap 解析 bridge 默认实现。
 *
 * 当前阶段定位：
 * 1. 它是上层访问 pcap 解码能力的 bridge 实现
 * 2. 它内部不再直接依赖 tshark 细节
 * 3. 它只负责把正式请求转发给 PcapDecodeGateway
 */
@Service
public class PcapParseBridgeServiceImpl implements PcapParseBridgeService {

    /**
     * pcap 解码正式网关。
     */
    private final PcapDecodeGateway pcapDecodeGateway;

    /**
     * 构造 pcap bridge 默认实现。
     *
     * @param pcapDecodeGateway pcap 解码网关
     */
    public PcapParseBridgeServiceImpl(PcapDecodeGateway pcapDecodeGateway) {
        this.pcapDecodeGateway = pcapDecodeGateway;
    }

    /**
     * 正式入口：处理一个 pcap 解码请求。
     *
     * 当前职责很单纯：
     * 1. 作为 bridge 层入口
     * 2. 把请求转发给正式 gateway
     *
     * @param request pcap 解码请求
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public void parse(PcapDecodeRequest request) throws Exception {
        pcapDecodeGateway.decode(request);
    }
}
