package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkBridgeException;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkBridgeProperties;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkJniBridgeClient;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeWiresharkJniBridgeClientTests {

    @Test
    void should_load_once_and_delegate_to_jni_bindings() throws Exception {
        Path fakeLibrary = Files.createTempFile("native-wireshark-jni", ".dll");
        try {
            NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
            properties.setEnabled(true);
            properties.setJniLibraryPath(fakeLibrary.toString());
            properties.setCoreLibraryPath("native\\wireshark-bridge\\build\\wireshark_native_bridge.dll");

            String[] loadedPaths = new String[2];
            int[] loadCount = {0};
            NativeWiresharkJniBridgeClient client = new NativeWiresharkJniBridgeClient(
                    properties,
                    path -> {
                        loadedPaths[loadCount[0]] = path.toString();
                        loadCount[0] = loadCount[0] + 1;
                    },
                    (payload, includeFieldTree, includeOffsets) -> """
                            {
                              "bridgeVersion": "phase1-jni-stub",
                              "protocolName": "nas-5gs",
                              "messageType": 65,
                              "messageTypeName": "Registration request",
                              "flatFields": {},
                              "fieldTree": [],
                              "diagnostics": []
                            }
                            """
            );

            String first = client.decodeNas5gs(new NativeWiresharkNasRequest(new byte[]{0x01}, true, true));
            String second = client.decodeNas5gs(new NativeWiresharkNasRequest(new byte[]{0x02}, false, false));

            assertEquals(2, loadCount[0]);
            assertEquals(true, loadedPaths[0].endsWith("native\\wireshark-bridge\\build\\wireshark_native_bridge.dll"));
            assertEquals(fakeLibrary.toAbsolutePath().normalize().toString(), loadedPaths[1]);
            assertEquals(true, first.contains("\"messageType\": 65"));
            assertEquals(true, second.contains("\"protocolName\": \"nas-5gs\""));
        } finally {
            Files.deleteIfExists(fakeLibrary);
        }
    }

    @Test
    void should_fail_when_jni_library_is_missing() {
        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        properties.setEnabled(true);
        properties.setJniLibraryPath("D:\\ideaterm\\5g-decrypt-system\\native\\wireshark-bridge\\missing.dll");

        NativeWiresharkJniBridgeClient client = new NativeWiresharkJniBridgeClient(
                properties,
                path -> {
                },
                (payload, includeFieldTree, includeOffsets) -> "{}"
        );

        NativeWiresharkBridgeException exception = assertThrows(
                NativeWiresharkBridgeException.class,
                () -> client.decodeNas5gs(new NativeWiresharkNasRequest(new byte[]{0x01}, true, true))
        );

        assertEquals(true, exception.getMessage().contains("does not exist"));
    }
}
