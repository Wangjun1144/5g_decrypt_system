package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkBridgeProperties;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkJniBridgeClient;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasRequest;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasResult;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeWiresharkJniBridgeIntegrationTests {

    @Test
    void should_load_built_jni_stub_and_decode_registration_request_shape() {
        assumeTrue(Boolean.getBoolean("native.wireshark.runJniIntegration"),
                "Enable with -Dnative.wireshark.runJniIntegration=true");
        Path preferredDir = Paths.get("native", "wireshark-bridge", "build-mingw");
        if (!preferredDir.toAbsolutePath().resolve("wireshark_native_bridge_jni.dll").toFile().exists()) {
            preferredDir = Paths.get("native", "wireshark-bridge", "build");
        }
        Path jniLibrary = preferredDir.resolve("wireshark_native_bridge_jni.dll")
                .toAbsolutePath()
                .normalize();
        Path coreLibrary = preferredDir.resolve("wireshark_native_bridge.dll")
                .toAbsolutePath()
                .normalize();

        assumeTrue(Files.exists(jniLibrary), "JNI bridge library must exist for integration test");
        assumeTrue(Files.exists(coreLibrary), "Core bridge library must exist for integration test");

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        properties.setEnabled(true);
        properties.setJniLibraryPath(jniLibrary.toString());
        properties.setCoreLibraryPath(coreLibrary.toString());

        NativeWiresharkJniBridgeClient client = new NativeWiresharkJniBridgeClient(properties);
        NativeWiresharkNasResult result = new NativeWiresharkResultMapper(new ObjectMapper()).mapNas5gs(
                client.decodeNas5gs(new NativeWiresharkNasRequest(
                        new byte[]{0x7e, 0x00, 0x41, 0x01},
                        true,
                        true
                ))
        );

        assertEquals("phase1-stub", result.getBridgeVersion());
        assertEquals("nas-5gs", result.getProtocolName());
        assertEquals(65, result.getMessageType());
        assertEquals("Registration request", result.getMessageTypeName());
        assertEquals("4", result.getFlatFields().get("bridge.stub_payload_length"));
        assertEquals("1", result.getFlatFields().get("bridge.runtime_available"));
        assertEquals("1", result.getFlatFields().get("bridge.runtime.epan_init"));
        assertEquals("0", result.getFlatFields().get("bridge.runtime.epan_init_call_succeeded"));
    }
}
