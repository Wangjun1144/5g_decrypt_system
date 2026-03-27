package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.decode.bridge.build.PcapBuildGateway;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import com.example.procedure.infrastructure.decode.HexCodec;
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
    // REFACTOR STEP: WIRESHARK_PACKAGE_PRUNE
    private PcapBuildGateway pcapBuildGateway;

    @Autowired
    // REFACTOR STEP: WIRESHARK_PACKAGE_PRUNE
    private PcapJsonDecodeGateway pcapJsonDecodeGateway;

    @Test
    void decode_rrc_plain_hex_to_json() throws Exception {
        // 1) 杈撳叆锛氭浛鎹㈡垚浣犺В瀵嗗悗鐨?RRC 鏄庢枃 hex锛堝厑璁稿甫绌烘牸/鍐掑彿/鎹㈣锛?
        String plainHex = "3a2fbf0121479913017ed421dbe7dc73430076d9da448b7d3f6931f4d55767c51ca845bef2ae228ac002e188cd69aee2521067a5ac225743b038cc92bd1f00b47c2ce60e64e2c87e39ef42c2a237c9ec508e3b85ea139f309fc691bf61c2836ad780";

        // 2) 鍥哄畾 DLT锛堜綘宸查獙璇侊細147 -> nr-rrc.ul.dcch锛?
        int dlt = 147;

        // 3) 宸ヤ綔鐩綍锛堢敓鎴?hexdump/pcap/json锛?
        Path workDir = Path.of("runtime", "wireshark_tmp");
        Files.createDirectories(workDir);

        // 鏂囦欢鍚嶅墠缂€锛堜究浜庡娆¤窇锛?
        String base = "rrc_plain_" + System.currentTimeMillis();

        Path hexdumpFile = workDir.resolve(base + ".txt");
        Path pcapFile = workDir.resolve(base + ".pcap");
        Path jsonFile = workDir.resolve(base + ".json");

        // 4) hex -> bytes -> text2pcap hexdump 鏂囨湰
        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);
        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);

        // 5) text2pcap 鐢熸垚 pcap锛堝啓鍏?DLT锛?
        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);

        // 6) tshark 瑙ｇ爜鎴?JSON
        String json = pcapJsonDecodeGateway.decodeToJson(pcapFile);

        // 7) 杈撳嚭 JSON 鏂囦欢 + 鎺у埗鍙伴瑙?
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
