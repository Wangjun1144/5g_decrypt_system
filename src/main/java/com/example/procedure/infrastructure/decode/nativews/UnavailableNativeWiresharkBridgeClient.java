package com.example.procedure.infrastructure.decode.nativews;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Default phase-1 bridge client. It keeps the Java-side contract stable until
 * the real JNI/JNA binding is wired in.
 */
public class UnavailableNativeWiresharkBridgeClient implements NativeWiresharkBridgeClient {

    private final NativeWiresharkBridgeProperties properties;

    public UnavailableNativeWiresharkBridgeClient(NativeWiresharkBridgeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String decodeNas5gs(NativeWiresharkNasRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NAS payload must not be empty");
        }
        Path jniPath = properties.jniLibraryPathOrNull();
        Path corePath = properties.coreLibraryPathOrNull();
        String jniHint = jniPath == null ? "<unset>" : jniPath.toString();
        String coreHint = corePath == null ? "<unset>" : corePath.toString();
        boolean jniPresent = jniPath != null && Files.exists(jniPath);
        boolean corePresent = corePath != null && Files.exists(corePath);
        throw new NativeWiresharkBridgeException(
                "Native Wireshark bridge is not wired yet. expectedJniLibrary="
                        + jniHint
                        + ", jniExists="
                        + jniPresent
                        + ", expectedCoreLibrary="
                        + coreHint
                        + ", coreExists="
                        + corePresent
                        + ", enabled="
                        + properties.isEnabled()
        );
    }
}
