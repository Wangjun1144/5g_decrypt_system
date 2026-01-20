package com.example.procedure.wireshark;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

@Component
public class Text2PcapService {

    private final WiresharkProperties props;

    public Text2PcapService(WiresharkProperties props) {
        this.props = props;
    }

    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        Files.createDirectories(outPcap.getParent());

        String exe = props.getText2pcapPath();
        if (exe == null || exe.isBlank()) throw new IllegalStateException("wireshark.text2pcapPath is empty");

        ProcessBuilder pb = new ProcessBuilder(List.of(
                exe, "-l", String.valueOf(dlt),
                hexdumpFile.toString(),
                outPcap.toString()
        ));
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("text2pcap failed (exit=" + code + ")\n" + out);

        return outPcap;
    }
}
