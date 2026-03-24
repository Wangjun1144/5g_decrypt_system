package com.example.procedure.wireshark;

import com.example.procedure.decodebridge.PcapBuildGateway;
import com.example.procedure.decodebridge.PcapJsonDecodeGateway;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Wireshark 解码服务。
 *
 * 当前定位：
 * 1. 这是面向现有调用方保留的较高层封装
 * 2. 它负责“明文 hex -> pcap -> tshark JSON”这一整段组合流程
 * 3. 当前内部已经不再直接依赖底层 text2pcap / tshark 实现
 *
 * 当前阶段的重要变化：
 * - 改为依赖正式的 PcapBuildGateway 和 PcapJsonDecodeGateway
 * - 这样旧服务也开始挂到新的 decode adapter 边界上
 */
@Service
public class WiresharkDecodeService {

    /**
     * hex 编解码工具。
     */
    private final HexCodec hexCodec;

    /**
     * pcap 构建正式网关。
     */
    private final PcapBuildGateway pcapBuildGateway;

    /**
     * pcap -> JSON 解码正式网关。
     */
    private final PcapJsonDecodeGateway pcapJsonDecodeGateway;

    /**
     * Wireshark 配置。
     */
    private final WiresharkProperties props;

    /**
     * 构造 Wireshark 解码服务。
     *
     * @param hexCodec hex 编解码工具
     * @param pcapBuildGateway pcap 构建网关
     * @param pcapJsonDecodeGateway JSON 解码网关
     * @param props Wireshark 配置
     */
    public WiresharkDecodeService(
            HexCodec hexCodec,
            PcapBuildGateway pcapBuildGateway,
            PcapJsonDecodeGateway pcapJsonDecodeGateway,
            WiresharkProperties props
    ) {
        this.hexCodec = hexCodec;
        this.pcapBuildGateway = pcapBuildGateway;
        this.pcapJsonDecodeGateway = pcapJsonDecodeGateway;
        this.props = props;
    }

    /**
     * 把明文 hex 封装成 pcap，再用 tshark 解码成 JSON。
     *
     * @param plainHex 解密得到的明文 hex
     * @param dlt 数据链路层类型
     * @param workDir 工作目录
     * @param baseName 基础文件名
     * @return tshark 解码后的 JSON 字符串
     * @throws Exception 构建或解码失败时抛出异常
     */
    public String decodeHexViaTshark(
            String plainHex,
            int dlt,
            Path workDir,
            String baseName
    ) throws Exception {
        Files.createDirectories(workDir);

        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        Path dumpFile = workDir.resolve(baseName + ".txt");
        Path pcapFile = workDir.resolve(baseName + ".pcap");

        Files.writeString(
                dumpFile,
                hexdump,
                StandardCharsets.US_ASCII,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        );

        pcapBuildGateway.buildPcap(dumpFile, dlt, pcapFile);

        return pcapJsonDecodeGateway.decodeToJson(pcapFile);
    }

    /**
     * 通过消息元数据自动推断 dissector 和 DLT，再完成解码。
     *
     * 当前支持：
     * 1. NAS
     * 2. RRC ul/dl + dcch/ccch
     *
     * @param plainHex 明文 hex
     * @param msgType 消息类型，支持 NAS / RRC
     * @param direction 方向，RRC 时使用 ul / dl
     * @param ch 信道，RRC 时使用 dcch / ccch
     * @param workDir 工作目录
     * @param baseName 基础文件名
     * @return tshark 解码后的 JSON 字符串
     * @throws Exception 参数非法、构建失败或解码失败时抛出异常
     */
    public String decodeHexByMeta(
            String plainHex,
            String msgType,
            String direction,
            String ch,
            Path workDir,
            String baseName
    ) throws Exception {
        if (plainHex == null || plainHex.isBlank()) {
            throw new IllegalArgumentException("plainHex is empty");
        }
        if (msgType == null || msgType.isBlank()) {
            throw new IllegalArgumentException("msgType is empty (NAS/RRC)");
        }
        if (workDir == null) {
            throw new IllegalArgumentException("workDir is null");
        }

        String mt = msgType.trim().toUpperCase(Locale.ROOT);

        final String dissector;
        if ("NAS".equals(mt)) {
            dissector = "nas-5gs";
        } else if ("RRC".equals(mt)) {
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("direction is empty (ul/dl) for RRC");
            }
            if (ch == null || ch.isBlank()) {
                throw new IllegalArgumentException("ch is empty (dcch/ccch) for RRC");
            }

            String dir = direction.trim().toLowerCase(Locale.ROOT);
            String chan = ch.trim().toLowerCase(Locale.ROOT);

            if (!("ul".equals(dir) || "dl".equals(dir))) {
                throw new IllegalArgumentException("direction must be ul/dl for RRC, got: " + direction);
            }
            if (!("dcch".equals(chan) || "ccch".equals(chan))) {
                throw new IllegalArgumentException("ch must be dcch/ccch for RRC, got: " + ch);
            }

            dissector = "nr-rrc." + dir + "." + chan;
        } else {
            throw new IllegalArgumentException("msgType must be NAS or RRC, got: " + msgType);
        }

        int dlt = findDltByDissector(dissector, props.getUserDlts());

        String bn = (baseName == null || baseName.isBlank())
                ? (mt.toLowerCase(Locale.ROOT) + "_"
                + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now()))
                : baseName;

        return decodeHexViaTshark(plainHex, dlt, workDir, bn);
    }

    /**
     * 根据 dissector 名称从配置中反查 DLT。
     *
     * @param dissector dissector 名称
     * @param userDlts 配置中的 DLT 映射
     * @return 对应的 DLT
     */
    private static int findDltByDissector(String dissector, Map<Integer, String> userDlts) {
        if (userDlts == null || userDlts.isEmpty()) {
            throw new IllegalStateException("wireshark.userDlts is empty; cannot map dissector to DLT");
        }

        String target = dissector.trim().toLowerCase(Locale.ROOT);

        return userDlts.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> e.getValue().trim().toLowerCase(Locale.ROOT).equals(target))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No DLT mapping found for dissector='" + dissector + "'. Check wireshark.userDlts.* config."
                ));
    }
}
