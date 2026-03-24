package com.example.procedure.decodebridge;

import com.example.procedure.wireshark.HexCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 明文转 pcap 服务默认实现。
 *
 * 当前职责：
 * 1. 把明文 hex 规范化并转成 hexdump
 * 2. 解析出对应 DLT
 * 3. 通过正式的 pcap 构建网关生成 pcap 文件
 *
 * 当前阶段的重要变化：
 * - 不再直接依赖 Text2PcapService
 * - 改为依赖正式的 PcapBuildGateway
 */
@Service
public class PlaintextToPcapServiceImpl implements PlaintextToPcapService {

    /**
     * hex 编解码工具。
     */
    private final HexCodec hexCodec;

    /**
     * pcap 构建正式网关。
     */
    private final PcapBuildGateway pcapBuildGateway;

    /**
     * DLT 解析器。
     */
    private final DltResolver dltResolver;

    /**
     * 构造明文转 pcap 服务。
     *
     * @param hexCodec hex 编解码工具
     * @param pcapBuildGateway pcap 构建网关
     * @param dltResolver DLT 解析器
     */
    public PlaintextToPcapServiceImpl(
            HexCodec hexCodec,
            PcapBuildGateway pcapBuildGateway,
            DltResolver dltResolver
    ) {
        this.hexCodec = hexCodec;
        this.pcapBuildGateway = pcapBuildGateway;
        this.dltResolver = dltResolver;
    }

    /**
     * 构建调试用 pcap 产物。
     *
     * 这个方法会：
     * 1. 校验请求
     * 2. 生成 hexdump 文件
     * 3. 生成 pcap 文件
     * 4. 返回完整的调试产物信息
     *
     * @param request 明文解码请求
     * @return 调试 pcap 构建结果
     * @throws Exception 构建失败时抛出异常
     */
    @Override
    public DebugPcapBuildResult buildDebugPcap(PlaintextDecodeRequest request) throws Exception {
        validateRequest(request);

        int dlt = dltResolver.resolve(request);
        String normalizedHex = normalizeHex(request.getPlainHex());

        byte[] bytes = hexCodec.decodeHex(normalizedHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        String base = buildBaseName(request);
        Path workDir = Path.of("runtime", "plaintext_decode", base);
        Files.createDirectories(workDir);

        Path hexdumpFile = workDir.resolve(base + ".txt");
        Path pcapFile = workDir.resolve(base + ".pcap");

        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);
        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);

        if (!request.isKeepHexdumpFile()) {
            try {
                Files.deleteIfExists(hexdumpFile);
                hexdumpFile = null;
            } catch (Exception ignored) {
            }
        }

        DebugPcapBuildResult result = new DebugPcapBuildResult();
        result.setWorkDir(workDir);
        result.setHexdumpFile(hexdumpFile);
        result.setPcapFile(pcapFile);
        result.setDlt(dlt);
        result.setNormalizedHex(normalizedHex);
        result.setByteLength(bytes.length);
        return result;
    }

    /**
     * 构建流式处理用 pcap 临时文件。
     *
     * 这个方法会创建临时目录和临时 pcap，
     * 供后续流式解码阶段使用。
     *
     * @param request 明文解码请求
     * @return 流式 pcap 句柄
     * @throws Exception 构建失败时抛出异常
     */
    @Override
    public StreamingPcapHandle buildStreamingPcap(PlaintextDecodeRequest request) throws Exception {
        validateRequest(request);

        int dlt = dltResolver.resolve(request);
        String normalizedHex = normalizeHex(request.getPlainHex());

        byte[] bytes = hexCodec.decodeHex(normalizedHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        Path tempDir = Files.createTempDirectory("plaintext_pcap_");
        Path hexdumpFile = tempDir.resolve("payload.txt");
        Path pcapFile = tempDir.resolve("payload.pcap");

        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);
        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);

        StreamingPcapHandle handle = new StreamingPcapHandle();
        handle.setTempDir(tempDir);
        handle.setHexdumpFile(hexdumpFile);
        handle.setPcapFile(pcapFile);
        handle.setDlt(dlt);
        handle.setByteLength(bytes.length);
        return handle;
    }

    /**
     * 校验明文解码请求。
     *
     * 当前校验项包括：
     * 1. 请求对象不能为空
     * 2. hex 不能为空
     * 3. 规范化后 hex 长度必须为偶数
     *
     * @param request 明文解码请求
     */
    private void validateRequest(PlaintextDecodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PlaintextDecodeRequest must not be null");
        }

        String normalizedHex = normalizeHex(request.getPlainHex());
        if (normalizedHex.isBlank()) {
            throw new IllegalArgumentException("plainHex must not be blank");
        }

        if ((normalizedHex.length() & 1) != 0) {
            throw new IllegalArgumentException("plainHex length must be even after normalization");
        }
    }

    /**
     * 规范化 hex 字符串。
     *
     * 当前会移除冒号和空白字符，
     * 方便后续统一按纯 hex 处理。
     *
     * @param plainHex 原始 hex 字符串
     * @return 规范化后的 hex
     */
    private String normalizeHex(String plainHex) {
        return plainHex == null ? "" : plainHex.replaceAll("[:\\s]", "");
    }

    /**
     * 构造调试目录和文件使用的基础名称。
     *
     * @param request 明文解码请求
     * @return 基础文件名
     */
    private String buildBaseName(PlaintextDecodeRequest request) {
        String trace = safeToken(request.getTraceId(), "noTrace");
        String msg = safeToken(request.getSourceMsgId(), "noMsg");
        String ue = safeToken(request.getUeId(), "noUe");
        return trace + "_" + ue + "_" + msg + "_" + System.currentTimeMillis();
    }

    /**
     * 把任意字符串转换成适合文件名使用的安全片段。
     *
     * @param s 原始字符串
     * @param fallback 为空时使用的兜底值
     * @return 安全字符串
     */
    private String safeToken(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
