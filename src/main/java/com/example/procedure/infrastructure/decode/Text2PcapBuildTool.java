package com.example.procedure.infrastructure.decode;

import com.example.procedure.wireshark.WiresharkProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 基于本地 text2pcap 的 pcap 构建工具。
 *
 * 当前职责：
 * 1. 校验 text2pcap 配置
 * 2. 调用本地 text2pcap 进程
 * 3. 生成目标 pcap 文件
 *
 * 这是 infrastructure.decode 包下的正式本地实现。
 */
@Component
public class Text2PcapBuildTool implements PcapBuildTool {

    /**
     * Wireshark 配置。
     */
    private final WiresharkProperties props;

    /**
     * 构造 text2pcap 构建工具。
     *
     * @param props Wireshark 配置
     */
    public Text2PcapBuildTool(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * 根据 hexdump 构建 pcap。
     *
     * @param hexdumpFile hexdump 文件
     * @param dlt DLT 类型
     * @param outPcap 输出 pcap 文件
     * @return 输出 pcap 文件路径
     * @throws Exception 构建失败时抛出异常
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
