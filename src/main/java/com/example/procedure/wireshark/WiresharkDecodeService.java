package com.example.procedure.wireshark;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
public class WiresharkDecodeService {

    private final HexCodec hexCodec;
    private final Text2PcapService text2PcapService;
    private final TsharkRunner tsharkRunner;

    private final WiresharkProperties props;

    public WiresharkDecodeService(HexCodec hexCodec,
                                  Text2PcapService text2PcapService,
                                  TsharkRunner tsharkRunner,
                                  WiresharkProperties props) {
        this.hexCodec = hexCodec;
        this.text2PcapService = text2PcapService;
        this.tsharkRunner = tsharkRunner;
        this.props = props;
    }


    /**
     * 把明文 hex 封成 pcap，再用 tshark 解码成 JSON 字段树。
     *
     * @param plainHex 解密得到的明文 hex
     * @param dlt      DLT（例如 147 -> nr-rrc.ul.dcch）
     * @param workDir  临时工作目录（例如 runtime/wireshark_tmp）
     * @param baseName 文件名前缀（便于排查）
     */
    public String decodeHexViaTshark(String plainHex, int dlt, Path workDir, String baseName) throws Exception {
        Files.createDirectories(workDir);

        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        Path dumpFile = workDir.resolve(baseName + ".txt");
        Path pcapFile = workDir.resolve(baseName + ".pcap");

        Files.writeString(dumpFile, hexdump, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        text2PcapService.buildPcap(dumpFile, dlt, pcapFile);

        return tsharkRunner.decodeToJson(pcapFile);
    }

    /**
     * 输入 hex + msgType(NAS/RRC) + 方向(ul/dl) + 信道(dcch/ccch)
     * 内部直接映射到 dissector -> 反查 DLT -> decodeHexViaTshark
     */
    public String decodeHexByMeta(String plainHex,
                                  String msgType,   // NAS / RRC
                                  String direction, // ul / dl
                                  String ch,        // dcch / ccch (RRC only)
                                  Path workDir,
                                  String baseName) throws Exception {
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

        // 1) meta -> dissector
        final String dissector;
        if ("NAS".equals(mt)) {
            // NAS 不需要 ul/dl、dcch/ccch
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

            // 这里直接生成 wireshark dissector 名
            // nr-rrc.ul.dcch / nr-rrc.dl.dcch / nr-rrc.ul.ccch / nr-rrc.dl.ccch
            dissector = "nr-rrc." + dir + "." + chan;
        }else {
            throw new IllegalArgumentException("msgType must be NAS or RRC, got: " + msgType);
        }

        // 2) dissector -> DLT（从配置反查，避免写死 147/151）
        int dlt = findDltByDissector(dissector, props.getUserDlts());

        // 3) baseName 兜底生成，避免并发覆盖
        String bn = (baseName == null || baseName.isBlank())
                ? (mt.toLowerCase(Locale.ROOT) + "_" +
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now()))
                : baseName;

        // 4) 直接走已有全流程
        return decodeHexViaTshark(plainHex, dlt, workDir, bn);
    }

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
