package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkBridgeClient;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkBridgeProperties;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasDecodeService;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasResult;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeWiresharkNasDecodeServiceTests {

    @Test
    void should_build_phase1_request_and_map_bridge_output() {
        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        properties.setIncludeFieldTree(true);
        properties.setIncludeOffsets(true);

        NativeWiresharkBridgeClient client = request -> {
            assertEquals(true, request.isIncludeFieldTree());
            assertEquals(true, request.isIncludeOffsets());
            assertEquals(3, request.getPayload().length);
            return """
                    {
                      "bridgeVersion": "phase1-stub",
                      "protocolName": "nas-5gs",
                      "messageType": 214,
                      "messageTypeName": "5GSM status",
                      "flatFields": {
                        "nas-5gs.sm.5gsm_cause": "42",
                        "bridge.stub_payload_length": "3",
                        "bridge.runtime_available": "1"
                      },
                      "fieldTree": [],
                      "diagnostics": []
                    }
                    """;
        };

        NativeWiresharkNasDecodeService service = new NativeWiresharkNasDecodeService(
                client,
                new NativeWiresharkResultMapper(new ObjectMapper()),
                properties
        );

        NativeWiresharkNasResult result = service.decodeNas5gs(new byte[]{0x2e, 0x05, 0x07});

        assertEquals("phase1-stub", result.getBridgeVersion());
        assertEquals(214, result.getMessageType());
        assertEquals("42", result.getFlatFields().get("nas-5gs.sm.5gsm_cause"));
        assertEquals("3", result.getFlatFields().get("bridge.stub_payload_length"));
        assertEquals("1", result.getFlatFields().get("bridge.runtime_available"));
    }
}
