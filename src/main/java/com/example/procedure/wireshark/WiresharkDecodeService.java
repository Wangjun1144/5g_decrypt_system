package com.example.procedure.wireshark;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Service
public class WiresharkDecodeService {

    private final HexCodec hexCodec;
    private final Text2PcapService text2PcapService;
    private final TsharkRunner tsharkRunner;

    public WiresharkDecodeService(HexCodec hexCodec,
                                  Text2PcapService text2PcapService,
                                  TsharkRunner tsharkRunner) {
        this.hexCodec = hexCodec;
        this.text2PcapService = text2PcapService;
        this.tsharkRunner = tsharkRunner;
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
}
