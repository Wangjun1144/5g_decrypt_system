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

/**
 * 基于本地 tshark 进程的 JSON 解码器。
 *
 * 当前职责：
 * 1. 校验 tshark 配置和 pcap 文件
 * 2. 构造 tshark 命令
 * 3. 执行本地进程并返回 JSON 输出
 *
 * 当前阶段定位：
 * - 这是 decode 基础设施最底层的本地实现
 * - 它不承担业务编排职责
 * - 它只负责“把 pcap 交给 tshark，拿回 JSON”
 */
@Component
public class LocalTsharkJsonDecoder implements PcapJsonDecoder {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(LocalTsharkJsonDecoder.class);

    /**
     * Wireshark 配置。
     */
    private final WiresharkProperties props;

    /**
     * 构造本地 tshark JSON 解码器。
     *
     * @param props Wireshark 配置
     */
    public LocalTsharkJsonDecoder(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * 一次性把 pcap 解码为 JSON 字符串。
     *
     * 当前适用于：
     * 1. 调试场景
     * 2. 小文件场景
     * 3. 需要把 JSON 落盘的场景
     *
     * @param pcapPath pcap 文件
     * @return JSON 字符串
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public String decodeToJson(Path pcapPath) throws Exception {
        validateInputs(pcapPath);

        String tsharkPath = props.getTsharkPath();
        List<String> cmd = buildJsonWithHexCommand(tsharkPath, pcapPath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        applyWiresharkConfig(pb);
        pb.redirectErrorStream(true);

        log.debug("Running tshark: {}", String.join(" ", cmd));

        Process process = pb.start();
        String out;
        try (InputStream is = process.getInputStream()) {
            out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int code = process.waitFor();
        if (code != 0) {
            throw new RuntimeException("tshark failed (exit=" + code + ")\n" + out);
        }

        return out;
    }

    /**
     * 流式把 pcap 解码成 JSON。
     *
     * 当前适用于：
     * 1. 大 pcap 文件
     * 2. 需要边解码边解析的场景
     * 3. 避免全量 JSON 占用内存的场景
     *
     * @param pcapPath pcap 文件
     * @param consumer JSON 输出流消费者
     * @throws Exception 解码失败时抛出异常
     */
    @Override
    public void decodeToJsonStream(Path pcapPath, Consumer<InputStream> consumer) throws Exception {
        validateInputs(pcapPath);

        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }

        String tsharkPath = props.getTsharkPath();
        List<String> cmd = buildJsonWithHexCommand(tsharkPath, pcapPath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        applyWiresharkConfig(pb);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder err = new StringBuilder();
        Thread drain = new Thread(() -> {
            try (InputStream es = process.getErrorStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = es.read(buf)) >= 0) {
                    err.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        }, "tshark-stderr");

        drain.setDaemon(true);
        drain.start();

        try (InputStream out = process.getInputStream()) {
            consumer.accept(out);
        } finally {
            int code = process.waitFor();
            drain.join(2000);
            if (code != 0) {
                throw new RuntimeException("tshark failed (exit=" + code + ")\n" + err);
            }
        }
    }

    /**
     * 校验解码所需输入。
     *
     * @param pcapPath pcap 文件
     */
    private void validateInputs(Path pcapPath) {
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
    }

    /**
     * 给 ProcessBuilder 应用 Wireshark 配置目录策略。
     *
     * @param pb 进程构建器
     */
    private void applyWiresharkConfig(ProcessBuilder pb) {
        Path configDir = props.configDirPathOrNull();
        if (configDir != null) {
            pb.environment().put("WIRESHARK_CONFIG_DIR", configDir.toString());
        } else if (props.isUseIsolatedConfig()) {
            pb.environment().put("WIRESHARK_CONFIG_DIR", props.cfgRootPath().toString());
        } else {
            pb.environment().remove("WIRESHARK_CONFIG_DIR");
        }
    }

    /**
     * 构造 tshark JSON 解码命令。
     *
     * @param tsharkPath tshark 可执行文件路径
     * @param pcapPath pcap 文件路径
     * @return 命令参数列表
     */
    private List<String> buildJsonWithHexCommand(String tsharkPath, Path pcapPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(tsharkPath);
        cmd.add("-l");
        cmd.add("-n");

        if (props.isEnableTlsDecryption()
                && props.getProfileName() != null
                && !props.getProfileName().isBlank()) {
            cmd.add("-C");
            cmd.add(props.getProfileName());
        }

        cmd.add("-r");
        cmd.add(pcapPath.toString());
        cmd.add("-T");
        cmd.add("json");
        cmd.add("-x");

        return cmd;
    }
}
