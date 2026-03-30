package com.example.procedure.infrastructure.decode;

import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs text2pcap to build a pcap file from a plaintext hexdump.
 */
@Component
public class Text2PcapBuildTool implements PcapBuildTool {

    /**
     * Wireshark configuration used to resolve the local text2pcap executable.
     */
    private final WiresharkProperties props;

    /**
     * Creates a text2pcap-backed pcap builder.
     */
    public Text2PcapBuildTool(WiresharkProperties props) {
        this.props = props;
    }

    /**
     * Builds a pcap file from a text2pcap-compatible hexdump file.
     */
    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        if (hexdumpFile == null || !Files.exists(hexdumpFile)) {
            throw new IllegalArgumentException("hexdump file not found: " + hexdumpFile);
        }
        if (outPcap == null) {
            throw new IllegalArgumentException("outPcap must not be null");
        }

        Path outputParent = outPcap.toAbsolutePath().normalize().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        Path executable = props.text2pcapPathOrNull();
        if (executable == null) {
            throw new IllegalStateException("wireshark.text2pcapPath is empty");
        }
        if (!Files.exists(executable)) {
            throw new IllegalStateException("text2pcap.exe not found: " + executable);
        }

        ProcessBuilder pb = new ProcessBuilder(List.of(
                executable.toString(),
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
