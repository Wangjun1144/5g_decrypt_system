package com.example.procedure.infrastructure.decode.nativews;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the phase-1 native Wireshark bridge.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "decode.native-wireshark")
public class NativeWiresharkBridgeProperties {

    /**
     * Whether the native bridge should be considered available for injection
     * into future decode flows.
     */
    private boolean enabled = false;

    /**
     * Absolute or workspace-relative path to the JNI bridge library loaded by
     * the JVM.
     */
    private String jniLibraryPath = "native\\wireshark-bridge\\build\\wireshark_native_bridge_jni.dll";

    /**
     * Absolute or workspace-relative path to the core bridge library that the
     * JNI shim delegates to.
     */
    private String coreLibraryPath = "native\\wireshark-bridge\\build-mingw\\wireshark_native_bridge.dll";

    /**
     * Absolute or workspace-relative path to the directly callable NAS DLL
     * built from the ws-core minimal runtime.
     */
    private String nasMinimalLibraryPath = "native\\ws-core\\build-nas-minimal\\ws_core_nas_minimal.dll";

    /**
     * Absolute or workspace-relative path to the directly callable NGAP DLL
     * built from the ws-core minimal runtime.
     */
    private String ngapMinimalLibraryPath = "native\\ws-core\\build-ngap-minimal\\ws_core_ngap_minimal.dll";

    /**
     * Absolute or workspace-relative path to the directly callable NR-RRC DLL
     * built from the ws-core minimal runtime.
     */
    private String nrRrcMinimalLibraryPath = "native\\ws-core\\build-nr-rrc-minimal\\ws_core_nr_rrc_minimal.dll";

    /**
     * Absolute or workspace-relative path to the directly callable MAC-NR
     * bearer-chain DLL built from the ws-core minimal runtime.
     */
    private String macNrChainMinimalLibraryPath = "native\\ws-core\\build-mac-nr-chain-minimal\\ws_core_mac_nr_chain_minimal.dll";

    /**
     * Additional runtime directories that contain dependent DLLs required by
     * the minimal ws-core builds.
     */
    private List<String> additionalRuntimeLibraryDirs = new ArrayList<>(List.of(
            "D:\\mingw64\\bin",
            "native\\ws-core\\deps\\msys2-glib\\mingw64\\bin",
            "native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin"
    ));

    /**
     * Base directory of an installed Wireshark runtime used during the current
     * transition phase.
     */
    private String wiresharkInstallDir = "D:\\wireshark";

    /**
     * Root directory of the project-owned Wireshark source slice that will
     * gradually replace the installed runtime dependency.
     */
    private String sourceSliceRoot = "native\\ws-core\\third_party\\wireshark-slice";

    /**
     * Whether the bridge should request tree nodes from the native layer.
     */
    private boolean includeFieldTree = true;

    /**
     * Whether offsets/lengths should be included in native tree output.
     */
    private boolean includeOffsets = true;

    /**
     * Whether callers should fail fast when the bridge is unavailable instead
     * of falling back to other decode implementations.
     */
    private boolean failFast = false;

    public Path jniLibraryPathOrNull() {
        if (jniLibraryPath == null || jniLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(jniLibraryPath).toAbsolutePath().normalize();
    }

    public Path coreLibraryPathOrNull() {
        if (coreLibraryPath == null || coreLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(coreLibraryPath).toAbsolutePath().normalize();
    }

    public Path wiresharkInstallDirOrNull() {
        if (wiresharkInstallDir == null || wiresharkInstallDir.isBlank()) {
            return null;
        }
        return Paths.get(wiresharkInstallDir).toAbsolutePath().normalize();
    }

    public Path sourceSliceRootOrNull() {
        if (sourceSliceRoot == null || sourceSliceRoot.isBlank()) {
            return null;
        }
        return Paths.get(sourceSliceRoot).toAbsolutePath().normalize();
    }

    public Path nasMinimalLibraryPathOrNull() {
        if (nasMinimalLibraryPath == null || nasMinimalLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(nasMinimalLibraryPath).toAbsolutePath().normalize();
    }

    public Path ngapMinimalLibraryPathOrNull() {
        if (ngapMinimalLibraryPath == null || ngapMinimalLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(ngapMinimalLibraryPath).toAbsolutePath().normalize();
    }

    public Path nrRrcMinimalLibraryPathOrNull() {
        if (nrRrcMinimalLibraryPath == null || nrRrcMinimalLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(nrRrcMinimalLibraryPath).toAbsolutePath().normalize();
    }

    public Path macNrChainMinimalLibraryPathOrNull() {
        if (macNrChainMinimalLibraryPath == null || macNrChainMinimalLibraryPath.isBlank()) {
            return null;
        }
        return Paths.get(macNrChainMinimalLibraryPath).toAbsolutePath().normalize();
    }

    public List<Path> additionalRuntimeLibraryDirsOrEmpty() {
        List<Path> resolved = new ArrayList<>();
        for (String dir : additionalRuntimeLibraryDirs) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            resolved.add(Paths.get(dir).toAbsolutePath().normalize());
        }
        return resolved;
    }
}
