package com.example.procedure.infrastructure.wireshark;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for local Wireshark and tshark integration.
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "wireshark")
public class WiresharkProperties {

    /**
     * Path to {@code tshark.exe}.
     */
    private String tsharkPath;

    /**
     * Optional path to {@code text2pcap.exe}.
     */
    private String text2pcapPath;

    /**
     * Isolated Wireshark config root used by this application.
     */
    private String cfgRoot = "runtime\\wireshark_cfg";

    /**
     * Wireshark profile name used by the application.
     */
    private String profileName = "rrc_decode";

    /**
     * DLT to dissector-name mapping for generated {@code user_dlts}.
     */
    private Map<Integer, String> userDlts = new LinkedHashMap<>();

    /**
     * Whether to use an isolated Wireshark config directory.
     */
    private boolean useIsolatedConfig = true;

    private boolean enableTlsDecryption = false;

    /**
     * Optional explicit override for {@code WIRESHARK_CONFIG_DIR}.
     */
    private String configDir;

    public Path cfgRootPath() {
        return Paths.get(cfgRoot).toAbsolutePath().normalize();
    }

    public Path configDirPathOrNull() {
        if (configDir == null || configDir.isBlank()) {
            return null;
        }
        return Paths.get(configDir).toAbsolutePath().normalize();
    }

    /**
     * Resolves the actual Wireshark config directory used by both profile initialization
     * and tshark process execution.
     */
    public Path activeConfigDirPathOrNull() {
        Path explicit = configDirPathOrNull();
        if (explicit != null) {
            return explicit;
        }
        if (useIsolatedConfig) {
            return cfgRootPath();
        }
        return null;
    }
}
