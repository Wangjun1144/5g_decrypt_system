package com.example.procedure.decodebridge;

import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.wireshark.PcapJsonDecoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

/**
 * 基于本地 JSON 解码器的 pcap 解码网关。
 *
 * 当前职责：
 * 1. 调用底层 PcapJsonDecoder 生成 JSON 流
 * 2. 调用 LayersSelectiveParser 把 JSON 流解析成 SignalingMessage
 * 3. 对上层隐藏 tshark + parser 的组合细节
 */
@Service
public class TsharkPcapDecodeGateway implements PcapDecodeGateway {

    /**
     * 底层 pcap JSON 解码器。
     */
    private final PcapJsonDecoder decoder;

    /**
     * 构造 pcap 解码网关。
     *
     * @param decoder 底层 pcap JSON 解码器
     */
    public TsharkPcapDecodeGateway(PcapJsonDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * 执行一次 pcap 解码。
     *
     * @param request pcap 解码请求
     * @throws Exception 解码或解析失败时抛出异常
     */
    @Override
    public void decode(PcapDecodeRequest request) throws Exception {
        validateRequest(request);

        ChainsInspectConsumer consumer = new ChainsInspectConsumer(request.getMessageConsumer());

        decoder.decodeToJsonStream(request.getPcap(), in -> {
            try {
                LayersSelectiveParser.parsePackets(
                        in,
                        request.getWanted(),
                        request.getEnabledRaw(),
                        consumer
                );
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to parse tshark json stream for pcap: " + request.getPcap(),
                        e
                );
            }
        });
    }

    /**
     * 校验解码请求对象。
     *
     * @param request pcap 解码请求
     */
    private void validateRequest(PcapDecodeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getPcap(), "pcap must not be null");
        Objects.requireNonNull(request.getWanted(), "wanted must not be null");
        Objects.requireNonNull(request.getEnabledRaw(), "enabledRaw must not be null");
        Objects.requireNonNull(request.getMessageConsumer(), "messageConsumer must not be null");
    }
}
