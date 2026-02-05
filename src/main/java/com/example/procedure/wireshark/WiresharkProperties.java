package com.example.procedure.wireshark;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "wireshark")
public class WiresharkProperties {

    /** e.g. D:\wireshark\tshark.exe */
    private String tsharkPath;

    /** e.g. D:\wireshark\text2pcap.exe (optional now) */
    private String text2pcapPath;

    /** isolated config root, e.g. runtime\wireshark_cfg */
    private String cfgRoot = "runtime\\wireshark_cfg";

    /** e.g. rrc_decode */
    private String profileName = "rrc_decode";


    /** DLT -> dissector name */
    private Map<Integer, String> userDlts = new LinkedHashMap<>();

    /**
     * If true: set WIRESHARK_CONFIG_DIR to cfgRoot (isolated config).
     * If false: don't set WIRESHARK_CONFIG_DIR, use system default (Roaming\Wireshark).
     */
    private boolean useIsolatedConfig = true;

    private boolean enableTlsDecryption = false;

    /**
     * Optional override for WIRESHARK_CONFIG_DIR.
     * Example: C:\Users\wjw15\AppData\Roaming\Wireshark
     * If set, it takes priority over useIsolatedConfig/cfgRoot.
     */
    private String configDir;

    public Path cfgRootPath() {
        return Paths.get(cfgRoot).toAbsolutePath().normalize();
    }

    public Path configDirPathOrNull() {
        if (configDir == null || configDir.isBlank()) return null;
        return Paths.get(configDir).toAbsolutePath().normalize();
    }
}
