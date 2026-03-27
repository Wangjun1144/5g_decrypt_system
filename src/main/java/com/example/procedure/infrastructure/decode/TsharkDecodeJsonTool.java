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
 * 鍩轰簬鏈湴 tshark 鐨?JSON 瑙ｇ爜宸ュ叿銆?
 *
 * 褰撳墠鑱岃矗锛?
 * 1. 鏍￠獙 tshark 閰嶇疆鍜岃緭鍏ユ枃浠?
 * 2. 鏋勯€?tshark 鍛戒护
 * 3. 鎵ц鏈湴杩涚▼骞惰繑鍥?JSON 杈撳嚭
 *
 * 杩欐槸 infrastructure.decode 鍖呬笅鐨勬寮忔湰鍦板疄鐜般€?
 */
@Component
public class TsharkDecodeJsonTool implements DecodeJsonTool {

    /**
     * 鏃ュ織鍣ㄣ€?
     */
    private static final Logger log = LoggerFactory.getLogger(TsharkDecodeJsonTool.class);

    /**
     * Wireshark 閰嶇疆銆?
     */
    private final WiresharkProperties props;

    /**
     * 鏋勯€?tshark JSON 瑙ｇ爜宸ュ叿銆?
     *
     * @param props Wireshark 閰嶇疆
     */
    public TsharkDecodeJsonTool(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * 涓€娆℃€ф妸 pcap 瑙ｇ爜鎴?JSON 瀛楃涓层€?
     *
     * @param pcapPath pcap 鏂囦欢璺緞
     * @return JSON 瀛楃涓?
     * @throws Exception 瑙ｇ爜澶辫触鏃舵姏鍑哄紓甯?
     */
    @Override
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
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
     * 娴佸紡鎶?pcap 瑙ｇ爜鎴?JSON銆?
     *
     * @param pcapPath pcap 鏂囦欢璺緞
     * @param consumer JSON 杈撳嚭娴佹秷璐硅€?
     * @throws Exception 瑙ｇ爜澶辫触鏃舵姏鍑哄紓甯?
     */
    @Override
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
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
     * 鏍￠獙瑙ｇ爜杈撳叆銆?
     *
     * @param pcapPath pcap 鏂囦欢璺緞
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
     * 搴旂敤 Wireshark 閰嶇疆鐩綍绛栫暐銆?
     *
     * @param pb 杩涚▼鏋勫缓鍣?
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
     * 鏋勯€?tshark JSON 瑙ｇ爜鍛戒护銆?
     *
     * @param tsharkPath tshark 鍙墽琛屾枃浠惰矾寰?
     * @param pcapPath pcap 鏂囦欢璺緞
     * @return 鍛戒护鍒楄〃
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
