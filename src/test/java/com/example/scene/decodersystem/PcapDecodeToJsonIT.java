package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(classes = Application.class)
class PcapDecodeToJsonIT {

    @Autowired
    // REFACTOR STEP: WIRESHARK_PACKAGE_PRUNE
    private PcapJsonDecodeGateway pcapJsonDecodeGateway;

    @Test
    void decode_existing_pcap_to_json() throws Exception {
        // 1) 杈撳叆锛氭浛鎹㈡垚浣犵殑 pcap 鏂囦欢璺緞锛堝缓璁斁 src/test/resources 閲岋級
        Path pcapPath = Path.of("5g_srsRAN_n78_gain40_amf.pcapng");
        if (!Files.exists(pcapPath)) {
            throw new IllegalArgumentException("pcap file not found: " + pcapPath.toAbsolutePath());
        }

        // 2) 宸ヤ綔鐩綍锛堢敓鎴?json锛?
        Path workDir = Path.of("runtime", "wireshark_tmp");
        Files.createDirectories(workDir);

        // 鏂囦欢鍚嶅墠缂€锛堜究浜庡娆¤窇锛?
        String base = "pcap_decode5g_" + System.currentTimeMillis();
        Path jsonFile = workDir.resolve(base + ".json");

        // 3) tshark 瑙ｇ爜鎴?JSON锛?T json -x锛?
        String json = pcapJsonDecodeGateway.decodeToJson(pcapPath);

        // 4) 杈撳嚭 JSON 鏂囦欢 + 鎺у埗鍙伴瑙?
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
