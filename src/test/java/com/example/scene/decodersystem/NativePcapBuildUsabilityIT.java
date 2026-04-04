package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.bridge.build.PcapBuildGateway;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that proves the pure-Java pcap builder produces a pcap file
 * that can still be consumed by the existing tshark JSON decode path.
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "decode.capture-build.mode=native",
                "decode.capture-build.fallback-to-external=false"
        }
)
class NativePcapBuildUsabilityIT {

    @Autowired
    private HexCodec hexCodec;

    @Autowired
    private PcapBuildGateway pcapBuildGateway;

    @Autowired
    private PcapJsonDecodeGateway pcapJsonDecodeGateway;

    @Test
    void native_generated_pcap_should_be_decodable_by_tshark() throws Exception {
        String plainHex = "3a2fbf0121479913017ed421dbe7dc73430076d9da448b7d3f6931f4d55767c51ca845bef2ae228ac002e188cd69aee2521067a5ac225743b038cc92bd1f00b47c2ce60e64e2c87e39ef42c2a237c9ec508e3b85ea139f309fc691bf61c2836ad780";
        int dlt = 147;

        Path workDir = Path.of("runtime", "native_pcap_tmp");
        Files.createDirectories(workDir);

        String base = "native_rrc_" + System.currentTimeMillis();
        Path hexdumpFile = workDir.resolve(base + ".txt");
        Path pcapFile = workDir.resolve(base + ".pcap");
        Path jsonFile = workDir.resolve(base + ".json");

        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);
        Files.writeString(hexdumpFile, hexdump, StandardCharsets.US_ASCII);

        pcapBuildGateway.buildPcap(hexdumpFile, dlt, pcapFile);
        assertTrue(Files.exists(pcapFile), "native builder should create the pcap file");
        assertTrue(Files.size(pcapFile) > 24 + 16, "pcap should contain global header, packet header, and payload");

        String json = pcapJsonDecodeGateway.decodeToJson(pcapFile);
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

        assertFalse(json.isBlank(), "tshark decode result should not be blank");
        assertTrue(json.contains("layers"), "decoded JSON should contain tshark packet layers");

        System.out.println("=== Native pcap usability check done ===");
        System.out.println("hexdump: " + hexdumpFile.toAbsolutePath());
        System.out.println("pcap   : " + pcapFile.toAbsolutePath());
        System.out.println("json   : " + jsonFile.toAbsolutePath());
        System.out.println("bytes  : " + bytes.length);
    }
}
