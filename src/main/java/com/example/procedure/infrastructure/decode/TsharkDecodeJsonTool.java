package com.example.procedure.infrastructure.decode;

import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
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
 * Runs tshark and exposes packet JSON either as a full string or as a stream.
 */
@Component
public class TsharkDecodeJsonTool implements DecodeJsonTool {

    /**
     * Logger for tshark execution and diagnostics.
     */
    private static final Logger log = LoggerFactory.getLogger(TsharkDecodeJsonTool.class);

    /**
     * Wireshark configuration shared by profile initialization and process execution.
     */
    private final WiresharkProperties props;

    /**
     * Creates a tshark-backed JSON decode tool.
     */
    public TsharkDecodeJsonTool(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * Fully decodes a pcap file to a JSON string.
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
     * Streams tshark JSON output to a downstream consumer.
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

        Throwable consumerFailure = null;
        try (InputStream out = process.getInputStream()) {
            consumer.accept(out);
        } catch (Throwable t) {
            consumerFailure = t;
        } finally {
            int code = process.waitFor();
            drain.join(2000);
            if (consumerFailure != null) {
                // Preserve the real downstream parsing/processing failure instead of
                // masking it with tshark's broken-pipe style exit code.
                if (code != 0) {
                    log.warn(
                            "tshark exited non-zero after downstream consumer failure. code={}, pcap={}, stderr={}",
                            code,
                            pcapPath,
                            err.toString().trim()
                    );
                }
                if (consumerFailure instanceof Exception exception) {
                    throw exception;
                }
                throw new RuntimeException(
                        "pcap json consumer failed for " + pcapPath,
                        consumerFailure
                );
            }
            if (code != 0) {
                throw new RuntimeException("tshark failed (exit=" + code + ")\n" + err);
            }
        }
    }

    /**
     * Validates the pcap path and tshark executable before spawning a process.
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
     * Applies the effective Wireshark config directory to the spawned process.
     */
    private void applyWiresharkConfig(ProcessBuilder pb) {
        Path configDir = props.activeConfigDirPathOrNull();
        if (configDir != null) {
            pb.environment().put("WIRESHARK_CONFIG_DIR", configDir.toString());
        } else {
            pb.environment().remove("WIRESHARK_CONFIG_DIR");
        }
    }

    /**
     * Builds the tshark command used for JSON plus hex export.
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
