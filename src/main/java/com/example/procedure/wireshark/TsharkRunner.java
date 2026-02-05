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
import java.util.function.Consumer;

@Component
public class TsharkRunner {

    private static final Logger log = LoggerFactory.getLogger(TsharkRunner.class);

    private final WiresharkProperties props;

    public TsharkRunner(WiresharkProperties props) {
        this.props = props;
    }




    /**
     * Decode pcap into a single JSON stream that includes both decoded layers and hex/raw info.
     * tshark: -T json -x
     *
     * NOTE: Still returns String (minimal change), but output may be huge. Next step is streaming.
     */
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

        List<String> cmd = buildJsonWithHexCommand(tsharkPath, pcapPath);

        ProcessBuilder pb = new ProcessBuilder(cmd);

        Path configDir = props.configDirPathOrNull();
        if (configDir != null) {
            // 显式指定使用某个配置目录（例如原 Roaming\Wireshark）
            pb.environment().put("WIRESHARK_CONFIG_DIR", configDir.toString());
        } else if (props.isUseIsolatedConfig()) {
            // 使用隔离配置
            pb.environment().put("WIRESHARK_CONFIG_DIR", props.cfgRootPath().toString());
        } else {
            // 不指定，让 tshark 用系统默认个人配置
            pb.environment().remove("WIRESHARK_CONFIG_DIR");
        }


        // 合并 stdout/stderr，便于排错
        pb.redirectErrorStream(true);

        log.debug("Running tshark: {}", String.join(" ", cmd));

        Process p = pb.start();
        String out;
        try (InputStream is = p.getInputStream()) {
            // NOTE: for huge pcaps, this can be big. We'll change to streaming next.
            out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = p.waitFor();

        if (code != 0) {
            throw new RuntimeException("tshark failed (exit=" + code + ")\n" + out);
        }
        return out;
    }

    public void decodeToJsonStream(Path pcapPath, Consumer<InputStream> consumer) throws Exception {
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

        List<String> cmd = buildJsonWithHexCommand(tsharkPath, pcapPath);
        ProcessBuilder pb = new ProcessBuilder(cmd);

        Path configDir = props.configDirPathOrNull();
        if (configDir != null) {
            pb.environment().put("WIRESHARK_CONFIG_DIR", configDir.toString());
        } else if (props.isUseIsolatedConfig()) {
            pb.environment().put("WIRESHARK_CONFIG_DIR", props.cfgRootPath().toString());
        } else {
            pb.environment().remove("WIRESHARK_CONFIG_DIR");
        }

        // ❌ 不要合并 stderr
        pb.redirectErrorStream(false);

        Process p = pb.start();

        // drain stderr 防止卡死
        StringBuilder err = new StringBuilder();
        Thread drain = new Thread(() -> {
            try (InputStream es = p.getErrorStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = es.read(buf)) >= 0) {
                    err.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        }, "tshark-stderr");
        drain.setDaemon(true);
        drain.start();

        try (InputStream out = p.getInputStream()) {
            consumer.accept(out); // ✅ 直接让 LayersSelectiveParser 从 stdout 解析
        } finally {
            int code = p.waitFor();
            drain.join(2000);
            if (code != 0) {
                throw new RuntimeException("tshark failed (exit=" + code + ")\n" + err);
            }
        }
    }


    private List<String> buildJsonWithHexCommand(String tsharkPath, Path pcapPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(tsharkPath);

        // 更适合 pipeline：line-buffered + no name resolution
        cmd.add("-l");
        cmd.add("-n");

        // profile（你原来就有）
        // ✅ 只有隔离模式才指定 profile（因为我们能保证它存在）
        if (props.isEnableTlsDecryption()
                && props.getProfileName() != null
                && !props.getProfileName().isBlank()) {
            cmd.add("-C");
            cmd.add(props.getProfileName());
        }

        cmd.add("-r");
        cmd.add(pcapPath.toString());

        // 单路输出：json + raw/hex
        cmd.add("-T");
        cmd.add("json");
        cmd.add("-x");

        // IMPORTANT: remove -V (verbose text), it conflicts with JSON intent.
        return cmd;
    }
}
