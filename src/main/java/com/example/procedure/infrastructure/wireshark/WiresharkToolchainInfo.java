package com.example.procedure.infrastructure.wireshark;

import java.nio.file.Path;

/**
 * Snapshot of the currently configured local Wireshark toolchain.
 */
public record WiresharkToolchainInfo(
        Path tsharkPath,
        boolean tsharkPresent,
        String tsharkVersion,
        Path text2pcapPath,
        boolean text2pcapPresent,
        String text2pcapVersion,
        Path activeConfigDir,
        Path activeProfileDir,
        Path userDltsFile,
        boolean userDltsPresent,
        String profileName,
        boolean isolatedConfig,
        int userDltCount
) {

    /**
     * Returns whether the minimum local tshark toolchain is ready for decode work.
     */
    public boolean isDecodeReady() {
        return tsharkPresent && userDltsPresent;
    }

    /**
     * Builds a compact log-friendly summary of the detected Wireshark toolchain.
     */
    public String toSummary() {
        return "tshark=" + describePath(tsharkPath, tsharkPresent)
                + ", tsharkVersion=" + valueOrPlaceholder(tsharkVersion)
                + ", text2pcap=" + describePath(text2pcapPath, text2pcapPresent)
                + ", text2pcapVersion=" + valueOrPlaceholder(text2pcapVersion)
                + ", configDir=" + valueOrPlaceholder(activeConfigDir)
                + ", profileDir=" + valueOrPlaceholder(activeProfileDir)
                + ", userDltsFile=" + describePath(userDltsFile, userDltsPresent)
                + ", profileName=" + valueOrPlaceholder(profileName)
                + ", isolatedConfig=" + isolatedConfig
                + ", userDltCount=" + userDltCount;
    }

    private static String describePath(Path path, boolean present) {
        if (path == null) {
            return "<unset>";
        }
        return path + " (" + (present ? "present" : "missing") + ")";
    }

    private static String valueOrPlaceholder(Object value) {
        return value == null ? "<unset>" : value.toString();
    }
}
