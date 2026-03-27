package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import com.example.procedure.infrastructure.decode.bridge.plaintext.HexToJsonDecodeService;
import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Integration-style checks around Wireshark-backed decode tooling.
 *
 * These tests exercise decode-oriented helper flows that are still useful for
 * offline inspection and operational troubleshooting, even though the mainline
 * processing path has moved toward streaming parse.
 */
@SpringBootTest(classes = Application.class)
class WiresharkDecodeIT {

    @Autowired
    private PcapJsonDecodeGateway pcapJsonDecodeGateway;

    @Autowired
    private WiresharkProperties props;

    @Autowired
    private HexToJsonDecodeService decodeService;

    /**
     * Decode one pcap file to tshark JSON and dump the result for inspection.
     */
    @Test
    void decode_pcap_and_dump_json() throws Exception {
        Path pcap = Path.of("runtime", "rrc_user0.pcap");
        Path out = Path.of("runtime", "tshark_out.json");

        System.out.println("=== WiresharkDecodeIT ===");
        System.out.println("time      : " + LocalDateTime.now());
        System.out.println("pcap      : " + pcap.toAbsolutePath());
        System.out.println("cfgRoot   : " + props.cfgRootPath());
        System.out.println("profile   : " + props.getProfileName());
        System.out.println("tsharkPath: " + props.getTsharkPath());
        System.out.println("out       : " + out.toAbsolutePath());

        if (!Files.exists(pcap)) {
            System.out.println("[ERROR] pcap not found: " + pcap.toAbsolutePath());
            return;
        }

        String json = pcapJsonDecodeGateway.decodeToJson(pcap);

        Files.createDirectories(out.getParent());
        Files.writeString(out, json, StandardCharsets.UTF_8);

        int previewLen = Math.min(json.length(), 2000);
        System.out.println("---- tshark json preview (first " + previewLen + " chars) ----");
        System.out.println(json.substring(0, previewLen));
        System.out.println("---- end preview ----");

        System.out.println("[OK] JSON dumped to: " + out.toAbsolutePath());
    }

    /**
     * Convert plaintext hex through decode helpers and dump the resulting JSON.
     */
    @Test
    void decode_hex_via_text2pcap_then_tshark_dump_json() throws Exception {
        Path nasHexFile = Path.of("runtime", "nas_plain.hex");
        Path rrcHexFile = Path.of("runtime", "rrc_ul_dcch_plain.hex");
        Path workDir = Path.of("runtime", "wireshark_tmp");
        Files.createDirectories(workDir);

        System.out.println("=== decode_hex_via_text2pcap_then_tshark_dump_json ===");
        System.out.println("time      : " + LocalDateTime.now());
        System.out.println("workDir   : " + workDir.toAbsolutePath());
        System.out.println("cfgRoot   : " + props.cfgRootPath());
        System.out.println("profile   : " + props.getProfileName());
        System.out.println("tsharkPath: " + props.getTsharkPath());
        System.out.println("text2pcap : " + props.getText2pcapPath());

        if (Files.exists(nasHexFile)) {
            String nasHex = Files.readString(nasHexFile, StandardCharsets.UTF_8).trim();

            String nasJson = decodeService.decodeHexByMeta(
                    nasHex,
                    "NAS",
                    null,
                    null,
                    workDir,
                    "it_nas"
            );

            Path nasOut = Path.of("runtime", "tshark_out_nas.json");
            Files.createDirectories(nasOut.getParent());
            Files.writeString(nasOut, nasJson, StandardCharsets.UTF_8);

            int previewLen = Math.min(nasJson.length(), 2000);
            System.out.println("---- NAS json preview (first " + previewLen + " chars) ----");
            System.out.println(nasJson.substring(0, previewLen));
            System.out.println("---- end NAS preview ----");

            System.out.println("[OK] NAS JSON dumped to: " + nasOut.toAbsolutePath());
        } else {
            System.out.println("[SKIP] nas hex file not found: " + nasHexFile.toAbsolutePath());
        }

        if (Files.exists(rrcHexFile)) {
            String rrcHex = Files.readString(rrcHexFile, StandardCharsets.UTF_8).trim();

            String rrcJson = decodeService.decodeHexByMeta(
                    rrcHex,
                    "RRC",
                    "ul",
                    "dcch",
                    workDir,
                    "it_rrc_ul_dcch"
            );

            Path rrcOut = Path.of("runtime", "tshark_out_rrc_ul_dcch.json");
            Files.createDirectories(rrcOut.getParent());
            Files.writeString(rrcOut, rrcJson, StandardCharsets.UTF_8);

            int previewLen = Math.min(rrcJson.length(), 2000);
            System.out.println("---- RRC UL DCCH json preview (first " + previewLen + " chars) ----");
            System.out.println(rrcJson.substring(0, previewLen));
            System.out.println("---- end RRC preview ----");

            System.out.println("[OK] RRC JSON dumped to: " + rrcOut.toAbsolutePath());
        } else {
            System.out.println("[SKIP] rrc hex file not found: " + rrcHexFile.toAbsolutePath());
        }
    }
}
