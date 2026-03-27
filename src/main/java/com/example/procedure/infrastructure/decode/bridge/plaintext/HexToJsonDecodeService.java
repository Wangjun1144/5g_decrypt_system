package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.build.PcapBuildGateway;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
public class HexToJsonDecodeService {

    // REFACTOR STEP: DECODEBRIDGE_REENTRY_SUBPACKAGE
    private final HexCodec hexCodec;
    private final PcapBuildGateway pcapBuildGateway;
    private final PcapJsonDecodeGateway pcapJsonDecodeGateway;
    private final WiresharkProperties props;

    public HexToJsonDecodeService(
            HexCodec hexCodec,
            PcapBuildGateway pcapBuildGateway,
            PcapJsonDecodeGateway pcapJsonDecodeGateway,
            WiresharkProperties props
    ) {
        this.hexCodec = hexCodec;
        this.pcapBuildGateway = pcapBuildGateway;
        this.pcapJsonDecodeGateway = pcapJsonDecodeGateway;
        this.props = props;
    }

    public String decodeHexViaTshark(
            String plainHex,
            int dlt,
            Path workDir,
            String baseName
    ) throws Exception {
        Files.createDirectories(workDir);

        byte[] bytes = hexCodec.decodeHex(plainHex);
        String hexdump = hexCodec.toText2PcapHexdump(bytes);

        Path dumpFile = workDir.resolve(baseName + ".txt");
        Path pcapFile = workDir.resolve(baseName + ".pcap");

        Files.writeString(
                dumpFile,
                hexdump,
                StandardCharsets.US_ASCII,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        );

        pcapBuildGateway.buildPcap(dumpFile, dlt, pcapFile);
        return pcapJsonDecodeGateway.decodeToJson(pcapFile);
    }

    public String decodeHexByMeta(
            String plainHex,
            String msgType,
            String direction,
            String ch,
            Path workDir,
            String baseName
    ) throws Exception {
        if (plainHex == null || plainHex.isBlank()) {
            throw new IllegalArgumentException("plainHex is empty");
        }
        if (msgType == null || msgType.isBlank()) {
            throw new IllegalArgumentException("msgType is empty (NAS/RRC)");
        }
        if (workDir == null) {
            throw new IllegalArgumentException("workDir is null");
        }

        String mt = msgType.trim().toUpperCase(Locale.ROOT);

        final String dissector;
        if ("NAS".equals(mt)) {
            dissector = "nas-5gs";
        } else if ("RRC".equals(mt)) {
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("direction is empty (ul/dl) for RRC");
            }
            if (ch == null || ch.isBlank()) {
                throw new IllegalArgumentException("ch is empty (dcch/ccch) for RRC");
            }

            String dir = direction.trim().toLowerCase(Locale.ROOT);
            String chan = ch.trim().toLowerCase(Locale.ROOT);

            if (!("ul".equals(dir) || "dl".equals(dir))) {
                throw new IllegalArgumentException("direction must be ul/dl for RRC, got: " + direction);
            }
            if (!("dcch".equals(chan) || "ccch".equals(chan))) {
                throw new IllegalArgumentException("ch must be dcch/ccch for RRC, got: " + ch);
            }

            dissector = "nr-rrc." + dir + "." + chan;
        } else {
            throw new IllegalArgumentException("msgType must be NAS or RRC, got: " + msgType);
        }

        int dlt = findDltByDissector(dissector, props.getUserDlts());
        String resolvedBaseName = (baseName == null || baseName.isBlank())
                ? (mt.toLowerCase(Locale.ROOT) + "_"
                + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now()))
                : baseName;

        return decodeHexViaTshark(plainHex, dlt, workDir, resolvedBaseName);
    }

    private static int findDltByDissector(String dissector, Map<Integer, String> userDlts) {
        if (userDlts == null || userDlts.isEmpty()) {
            throw new IllegalStateException("wireshark.userDlts is empty; cannot map dissector to DLT");
        }

        String target = dissector.trim().toLowerCase(Locale.ROOT);

        return userDlts.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> e.getValue().trim().toLowerCase(Locale.ROOT).equals(target))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No DLT mapping found for dissector='" + dissector + "'. Check wireshark.userDlts.* config."
                ));
    }
}
