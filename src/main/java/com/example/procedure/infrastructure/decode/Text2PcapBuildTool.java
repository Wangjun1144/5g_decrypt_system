package com.example.procedure.infrastructure.decode;

import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 鍩轰簬鏈湴 text2pcap 鐨?pcap 鏋勫缓宸ュ叿銆?
 *
 * 褰撳墠鑱岃矗锛?
 * 1. 鏍￠獙 text2pcap 閰嶇疆
 * 2. 璋冪敤鏈湴 text2pcap 杩涚▼
 * 3. 鐢熸垚鐩爣 pcap 鏂囦欢
 *
 * 杩欐槸 infrastructure.decode 鍖呬笅鐨勬寮忔湰鍦板疄鐜般€?
 */
@Component
public class Text2PcapBuildTool implements PcapBuildTool {

    /**
     * Wireshark 閰嶇疆銆?
     */
    private final WiresharkProperties props;

    /**
     * 鏋勯€?text2pcap 鏋勫缓宸ュ叿銆?
     *
     * @param props Wireshark 閰嶇疆
     */
    public Text2PcapBuildTool(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * 鏍规嵁 hexdump 鏋勫缓 pcap銆?
     *
     * @param hexdumpFile hexdump 鏂囦欢
     * @param dlt DLT 绫诲瀷
     * @param outPcap 杈撳嚭 pcap 鏂囦欢
     * @return 杈撳嚭 pcap 鏂囦欢璺緞
     * @throws Exception 鏋勫缓澶辫触鏃舵姏鍑哄紓甯?
     */
    @Override
    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        if (hexdumpFile == null || !Files.exists(hexdumpFile)) {
            throw new IllegalArgumentException("hexdump file not found: " + hexdumpFile);
        }
        if (outPcap == null) {
            throw new IllegalArgumentException("outPcap must not be null");
        }

        Files.createDirectories(outPcap.getParent());

        String exe = props.getText2pcapPath();
        if (exe == null || exe.isBlank()) {
            throw new IllegalStateException("wireshark.text2pcapPath is empty");
        }
        if (!Files.exists(Path.of(exe))) {
            throw new IllegalStateException("text2pcap.exe not found: " + exe);
        }

        ProcessBuilder pb = new ProcessBuilder(List.of(
                exe,
                "-l",
                String.valueOf(dlt),
                hexdumpFile.toString(),
                outPcap.toString()
        ));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();

        if (code != 0) {
            throw new RuntimeException("text2pcap failed (exit=" + code + ")\n" + out);
        }

        return outPcap;
    }
}
