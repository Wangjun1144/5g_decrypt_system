package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.wireshark.TsharkRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(classes = Application.class)
class PcapDecodeToJsonIT {

    @Autowired
    private TsharkRunner tsharkRunner;

    @Test
    void decode_existing_pcap_to_json() throws Exception {
        // 1) 输入：替换成你的 pcap 文件路径（建议放 src/test/resources 里）
        Path pcapPath = Path.of("5g_srsRAN_n78_gain40_amf.pcapng");
        if (!Files.exists(pcapPath)) {
            throw new IllegalArgumentException("pcap file not found: " + pcapPath.toAbsolutePath());
        }

        // 2) 工作目录（生成 json）
        Path workDir = Path.of("runtime", "wireshark_tmp");
        Files.createDirectories(workDir);

        // 文件名前缀（便于多次跑）
        String base = "pcap_decode5g_" + System.currentTimeMillis();
        Path jsonFile = workDir.resolve(base + ".json");

        // 3) tshark 解码成 JSON（-T json -x）
        String json = tsharkRunner.decodeToJson(pcapPath);

        // 4) 输出 JSON 文件 + 控制台预览
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

        System.out.println("=== PCAP decode done ===");
        System.out.println("pcap : " + pcapPath.toAbsolutePath());
        System.out.println("json : " + jsonFile.toAbsolutePath());

        int previewLen = Math.min(json.length(), 2000);
        System.out.println("---- JSON preview (first " + previewLen + " chars) ----");
        System.out.println(json.substring(0, previewLen));
        System.out.println("---- end preview ----");
    }
}
