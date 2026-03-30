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
     * Whether to verify the local Wireshark toolchain during application startup.
     */
    private boolean verifyOnStartup = true;

    /**
     * Whether startup should fail when the Wireshark toolchain is invalid.
     */
    private boolean failFastOnInvalidToolchain = false;

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

    public Path tsharkPathOrNull() {
        if (tsharkPath == null || tsharkPath.isBlank()) {
            return null;
        }
        return Paths.get(tsharkPath).toAbsolutePath().normalize();
    }

    public Path text2pcapPathOrNull() {
        if (text2pcapPath == null || text2pcapPath.isBlank()) {
            return null;
        }
        return Paths.get(text2pcapPath).toAbsolutePath().normalize();
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

    /**
     * Resolves the profile directory used by tshark when a config directory is active.
     */
    public Path activeProfileDirPathOrNull() {
        Path configDirPath = activeConfigDirPathOrNull();
        if (configDirPath == null) {
            return null;
        }
        return configDirPath.resolve("profiles").resolve(profileName);
    }

    /**
     * Resolves the generated {@code user_dlts} file when a profile directory is active.
     */
    public Path userDltsFilePathOrNull() {
        Path profileDir = activeProfileDirPathOrNull();
        if (profileDir == null) {
            return null;
        }
        return profileDir.resolve("user_dlts");
    }
}
