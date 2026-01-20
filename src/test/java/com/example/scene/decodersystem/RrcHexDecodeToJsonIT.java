package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.wireshark.HexCodec;
import com.example.procedure.wireshark.Text2PcapService;
import com.example.procedure.wireshark.TsharkRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(classes = Application.class)
class RrcHexDecodeToJsonIT {

    @Autowired
    private HexCodec hexCodec;

    @Autowired
    private Text2PcapService text2PcapService;

    @Autowired
    private TsharkRunner tsharkRunner;

    @Test
    void decode_rrc_plain_hex_to_json() throws Exception {
        // 1) 输入：替换成你解密后的 RRC 明文 hex（允许带空格/冒号/换行）
        String plainHex = "3a2fbf0121479913017ed421dbe7dc73430076d9da448b7d3f6931f4d55767c51ca845bef2ae228ac002e188cd69aee2521067a5ac225743b038cc92bd1f00b47c2ce60e64e2c87e39ef42c2a237c9ec508e3b85ea139f309fc691bf61c2836ad780";

        // 2) 固定 DLT（你已验证：147 -> nr-rrc.ul.dcch）
        int dlt = 147;

        // 3) 工作目录（生成 hexdump/pcap/json）
        Path workDir = Path.of("runtime", "wireshark_tmp");
        Files.createDirectories(workDir);

        // 文件名前缀（便于多次跑）
        String base = "rrc_plain_" + System.currentTimeMillis();

        Path hexdumpFile = workDir.resolve(base + ".txt");
        Path pcapFile = workDir.resolve(base + ".pcap");
        Path jsonFile = workDir.resolve(base + ".json");

        // 4) hex -> bytes -> text2pcap hexdump 文本
        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);
        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);

        // 5) text2pcap 生成 pcap（写入 DLT）
        text2PcapService.buildPcap(hexdumpFile, dlt, pcapFile);

        // 6) tshark 解码成 JSON
        String json = tsharkRunner.decodeToJson(pcapFile);

        // 7) 输出 JSON 文件 + 控制台预览
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

        System.out.println("=== RRC decode done ===");
        System.out.println("hexdump: " + hexdumpFile.toAbsolutePath());
        System.out.println("pcap   : " + pcapFile.toAbsolutePath());
        System.out.println("json   : " + jsonFile.toAbsolutePath());

        int previewLen = Math.min(json.length(), 2000);
        System.out.println("---- JSON preview (first " + previewLen + " chars) ----");
        System.out.println(json.substring(0, previewLen));
        System.out.println("---- end preview ----");
    }
}
