package com.example.procedure.infrastructure.wireshark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the locally installed Wireshark toolchain and logs the effective runtime setup.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class WiresharkEnvironmentVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WiresharkEnvironmentVerifier.class);

    private final WiresharkProperties props;

    public WiresharkEnvironmentVerifier(WiresharkProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isVerifyOnStartup()) {
            log.info("Wireshark toolchain verification skipped because wireshark.verifyOnStartup=false");
            return;
        }

        WiresharkToolchainInfo info = inspectToolchain();
        if (info.isDecodeReady()) {
            log.info("Wireshark toolchain verified. {}", info.toSummary());
            return;
        }

        String message = "Wireshark toolchain is incomplete. " + info.toSummary();
        if (props.isFailFastOnInvalidToolchain()) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }

    /**
     * Inspects the configured local tools, profile files and generated DLT mapping.
     */
    public WiresharkToolchainInfo inspectToolchain() {
        Path tshark = props.tsharkPathOrNull();
        Path text2pcap = props.text2pcapPathOrNull();
        Path configDir = props.activeConfigDirPathOrNull();
        Path profileDir = props.activeProfileDirPathOrNull();
        Path userDltsFile = props.userDltsFilePathOrNull();

        boolean tsharkPresent = isExecutablePresent(tshark);
        boolean text2pcapPresent = isExecutablePresent(text2pcap);
        boolean userDltsPresent = userDltsFile != null && Files.exists(userDltsFile);

        return new WiresharkToolchainInfo(
                tshark,
                tsharkPresent,
                readVersionLine(tshark, "-v"),
                text2pcap,
                text2pcapPresent,
                readVersionLine(text2pcap, "-h"),
                configDir,
                profileDir,
                userDltsFile,
                userDltsPresent,
                props.getProfileName(),
                props.isUseIsolatedConfig(),
                props.getUserDlts() == null ? 0 : props.getUserDlts().size()
        );
    }

    /**
     * Returns whether the configured executable exists on disk.
     */
    private boolean isExecutablePresent(Path executable) {
        return executable != null && Files.exists(executable);
    }

    /**
     * Invokes the tool with a lightweight version/help flag and returns the first output line.
     */
    private String readVersionLine(Path executable, String probeArg) {
        if (!isExecutablePresent(executable)) {
            return null;
        }

        try {
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.add(probeArg);

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            byte[] stdout;
            byte[] stderr;
            try (InputStream out = process.getInputStream(); InputStream err = process.getErrorStream()) {
                stdout = out.readAllBytes();
                stderr = err.readAllBytes();
            }

            process.waitFor();

            String firstStdout = firstNonBlankLine(new String(stdout, StandardCharsets.UTF_8));
            if (firstStdout != null) {
                return firstStdout;
            }
            return firstNonBlankLine(new String(stderr, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return "probe-failed: " + ex.getClass().getSimpleName();
        }
    }

    private String firstNonBlankLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return null;
    }
}
