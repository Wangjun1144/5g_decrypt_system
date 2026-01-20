package com.example.procedure.wireshark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class TsharkRunner {

    private static final Logger log = LoggerFactory.getLogger(TsharkRunner.class);

    private final WiresharkProperties props;

    public TsharkRunner(WiresharkProperties props) {
        this.props = props;
    }

    public String decodeToJson(Path pcapPath) throws Exception {
        if (pcapPath == null || !Files.exists(pcapPath)) {
            throw new IllegalArgumentException("pcap file not found: " + pcapPath);
        }

        String tsharkPath = props.getTsharkPath();
        if (tsharkPath == null || tsharkPath.isBlank()) {
            throw new IllegalStateException("wireshark.tsharkPath is empty");
        }
        if (!Files.exists(Path.of(tsharkPath))) {
            throw new IllegalStateException("tshark.exe not found: " + tsharkPath);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(tsharkPath);
        cmd.add("-C");
        cmd.add(props.getProfileName());
        cmd.add("-r");
        cmd.add(pcapPath.toString());
        cmd.add("-T");
        cmd.add("json");
        cmd.add("-V");

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // 强制使用隔离配置目录（关键）
        pb.environment().put("WIRESHARK_CONFIG_DIR", props.cfgRootPath().toString());

        // 合并 stdout/stderr，便于排错
        pb.redirectErrorStream(true);

        log.debug("Running tshark: {}", String.join(" ", cmd));

        Process p = pb.start();
        String out;
        try (InputStream is = p.getInputStream()) {
            out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = p.waitFor();

        if (code != 0) {
            throw new RuntimeException("tshark failed (exit=" + code + ")\n" + out);
        }
        return out;
    }
}
