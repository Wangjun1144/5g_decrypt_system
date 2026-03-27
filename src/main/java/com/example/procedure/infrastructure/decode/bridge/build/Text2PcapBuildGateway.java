package com.example.procedure.infrastructure.decode.bridge.build;

import com.example.procedure.infrastructure.decode.Text2PcapBuildTool;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Default build gateway backed by the infrastructure text2pcap tool.
 */
@Service
public class Text2PcapBuildGateway implements PcapBuildGateway {

    // REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
    // REFACTOR STEP: DECODEBRIDGE_BUILD_JSON_SUBPACKAGE
    private final Text2PcapBuildTool builder;

    public Text2PcapBuildGateway(Text2PcapBuildTool builder) {
        this.builder = builder;
    }

    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        Objects.requireNonNull(hexdumpFile, "hexdumpFile must not be null");
        Objects.requireNonNull(outPcap, "outPcap must not be null");
        return builder.buildPcap(hexdumpFile, dlt, outPcap);
    }
}
