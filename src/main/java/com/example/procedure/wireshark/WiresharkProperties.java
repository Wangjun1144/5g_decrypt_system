package com.example.procedure.wireshark;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "wireshark")
public class WiresharkProperties {

    /** e.g. C:\Program Files\Wireshark\tshark.exe */
    private String tsharkPath;

    /** e.g. C:\Program Files\Wireshark\text2pcap.exe (optional now) */
    private String text2pcapPath;

    /** e.g. runtime\wireshark_cfg */
    private String cfgRoot = "runtime\\wireshark_cfg";

    /** e.g. rrc_decode */
    private String profileName = "rrc_decode";

    /**
     * user_dlts mapping: DLT -> dissector name, e.g. 147 -> nr-rrc
     * from properties: wireshark.userDlts.147=nr-rrc
     */
    private Map<Integer, String> userDlts = new LinkedHashMap<>();

    public String getTsharkPath() { return tsharkPath; }
    public void setTsharkPath(String tsharkPath) { this.tsharkPath = tsharkPath; }

    public String getText2pcapPath() { return text2pcapPath; }
    public void setText2pcapPath(String text2pcapPath) { this.text2pcapPath = text2pcapPath; }

    public String getCfgRoot() { return cfgRoot; }
    public void setCfgRoot(String cfgRoot) { this.cfgRoot = cfgRoot; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public Map<Integer, String> getUserDlts() { return userDlts; }
    public void setUserDlts(Map<Integer, String> userDlts) { this.userDlts = userDlts; }

    public Path cfgRootPath() {
        return Paths.get(cfgRoot).toAbsolutePath().normalize();
    }
}
