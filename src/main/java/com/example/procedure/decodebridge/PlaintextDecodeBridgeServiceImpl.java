package com.example.procedure.decodebridge;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 明文解码 bridge 默认实现。
 *
 * 当前职责：
 * 1. 先把明文构造成 pcap
 * 2. 再通过正式 JSON 解码网关生成 tshark JSON 输出
 * 3. 对上层隐藏 pcap 构建和 tshark 解码的组合细节
 *
 * 当前阶段的重要变化：
 * - 不再直接依赖 TsharkRunner
 * - 改为依赖正式的 PcapJsonDecodeGateway
 */
@Service
public class PlaintextDecodeBridgeServiceImpl implements PlaintextDecodeBridgeService {

    /**
     * 明文转 pcap 服务。
     */
    private final PlaintextToPcapService plaintextToPcapService;

    /**
     * pcap -> JSON 解码正式网关。
     */
    private final PcapJsonDecodeGateway pcapJsonDecodeGateway;

    /**
     * 构造明文解码 bridge。
     *
     * @param plaintextToPcapService 明文转 pcap 服务
     * @param pcapJsonDecodeGateway JSON 解码网关
     */
    public PlaintextDecodeBridgeServiceImpl(
            PlaintextToPcapService plaintextToPcapService,
            PcapJsonDecodeGateway pcapJsonDecodeGateway
    ) {
        this.plaintextToPcapService = plaintextToPcapService;
        this.pcapJsonDecodeGateway = pcapJsonDecodeGateway;
    }

    /**
     * 构建调试用完整解码产物。
     *
     * 当前会：
     * 1. 先构建调试 pcap
     * 2. 再把 pcap 一次性解码成 JSON
     * 3. 把 JSON 文件也落到调试目录中
     *
     * @param request 明文解码请求
     * @return 调试解码产物
     * @throws Exception 构建或解码失败时抛出异常
     */
    @Override
    public DebugDecodeArtifacts buildDebugArtifacts(PlaintextDecodeRequest request) throws Exception {
        DebugPcapBuildResult pcapResult = plaintextToPcapService.buildDebugPcap(request);

        String json = pcapJsonDecodeGateway.decodeToJson(pcapResult.getPcapFile());

        String baseName = stripExtension(pcapResult.getPcapFile().getFileName().toString());
        Path jsonFile = pcapResult.getWorkDir().resolve(baseName + ".json");
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

        DebugDecodeArtifacts result = new DebugDecodeArtifacts();
        result.setWorkDir(pcapResult.getWorkDir());
        result.setHexdumpFile(pcapResult.getHexdumpFile());
        result.setPcapFile(pcapResult.getPcapFile());
        result.setJsonFile(jsonFile);
        result.setDlt(pcapResult.getDlt());
        result.setByteLength(pcapResult.getByteLength());
        return result;
    }

    /**
     * 流式输出明文解码后的 JSON。
     *
     * 当前会：
     * 1. 构建一个临时 pcap
     * 2. 通过 JSON 解码网关把 stdout 流交给上层
     * 3. 使用 try-with-resources 自动清理临时文件
     *
     * @param request 明文解码请求
     * @param jsonConsumer JSON 输出流消费者
     * @throws Exception 构建或解码失败时抛出异常
     */
    @Override
    public void streamDecodedJson(
            PlaintextDecodeRequest request,
            Consumer<InputStream> jsonConsumer
    ) throws Exception {
        if (jsonConsumer == null) {
            throw new IllegalArgumentException("jsonConsumer must not be null");
        }

        try (StreamingPcapHandle handle = plaintextToPcapService.buildStreamingPcap(request)) {
            pcapJsonDecodeGateway.decodeToJsonStream(handle.getPcapFile(), jsonConsumer);
        }
    }

    /**
     * 去掉文件扩展名。
     *
     * @param fileName 原始文件名
     * @return 去掉扩展名后的文件名
     */
    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? fileName : fileName.substring(0, idx);
    }
}
