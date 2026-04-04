package com.example.procedure.infrastructure.decode.bridge.build;

import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import com.example.procedure.infrastructure.decode.Text2PcapBuildTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Build gateway that can use either the native Java pcap writer or text2pcap.
 */
@Service
public class Text2PcapBuildGateway implements PcapBuildGateway {

    private final NativePcapBuildTool nativeBuilder;
    private final Text2PcapBuildTool externalBuilder;
    private final String mode;
    private final boolean fallbackToExternal;

    public Text2PcapBuildGateway(
            NativePcapBuildTool nativeBuilder,
            Text2PcapBuildTool externalBuilder,
            @Value("${decode.capture-build.mode:native}") String mode,
            @Value("${decode.capture-build.fallback-to-external:true}") boolean fallbackToExternal
    ) {
        this.nativeBuilder = nativeBuilder;
        this.externalBuilder = externalBuilder;
        this.mode = mode == null ? "native" : mode.trim().toLowerCase();
        this.fallbackToExternal = fallbackToExternal;
    }

    @Override
    public Path buildPcap(Path hexdumpFile, int dlt, Path outPcap) throws Exception {
        Objects.requireNonNull(hexdumpFile, "hexdumpFile must not be null");
        Objects.requireNonNull(outPcap, "outPcap must not be null");

        if ("external".equals(mode)) {
            return externalBuilder.buildPcap(hexdumpFile, dlt, outPcap);
        }

        try {
            return nativeBuilder.buildPcap(hexdumpFile, dlt, outPcap);
        } catch (Exception ex) {
            if (!fallbackToExternal || "native".equals(mode) && !isExternalAvailable()) {
                throw ex;
            }
            return externalBuilder.buildPcap(hexdumpFile, dlt, outPcap);
        }
    }

    private boolean isExternalAvailable() {
        try {
            return externalBuilder != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
