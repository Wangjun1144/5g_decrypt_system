package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.wireshark.TsharkRunner;
import com.example.procedure.wireshark.WiresharkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@SpringBootTest(classes = Application.class)
class WiresharkDecodeIT {

    @Autowired
    private TsharkRunner tsharkRunner;

    @Autowired
    private WiresharkProperties props;

    @Test
    void decode_pcap_and_dump_json() throws Exception {
        // 1) 输入 pcap（改成你实际文件名）
        Path pcap = Path.of("runtime", "rrc_user0.pcap");

        // 2) 输出文件：把 tshark JSON 写出来
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
            return; // 不抛断言，直接返回
        }

        // 3) 调 tshark
        String json = tsharkRunner.decodeToJson(pcap);

        // 4) 写文件（确保目录存在）
        Files.createDirectories(out.getParent());
        Files.writeString(out, json, StandardCharsets.UTF_8);

        // 5) 打印一小段到控制台（避免太大）
        int previewLen = Math.min(json.length(), 2000);
        System.out.println("---- tshark json preview (first " + previewLen + " chars) ----");
        System.out.println(json.substring(0, previewLen));
        System.out.println("---- end preview ----");

        System.out.println("[OK] JSON dumped to: " + out.toAbsolutePath());
    }
}

