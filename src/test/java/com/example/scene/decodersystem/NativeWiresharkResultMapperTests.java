package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasResult;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeWiresharkResultMapperTests {

    @Test
    void should_map_native_nas_result_json_into_project_model() {
        NativeWiresharkResultMapper mapper = new NativeWiresharkResultMapper(new ObjectMapper());

        NativeWiresharkNasResult result = mapper.mapNas5gs("""
                {
                  "bridgeVersion": "phase1",
                  "protocolName": "nas-5gs",
                  "messageType": 65,
                  "messageTypeName": "Registration request",
                  "flatFields": {
                    "nas-5gs.mm.5gs_reg_type": "1"
                  },
                  "fieldTree": [
                    {
                      "name": "Registration request",
                      "value": "",
                      "offset": 0,
                      "length": 3,
                      "children": [
                        {
                          "name": "nas-5gs.mm.5gs_reg_type",
                          "value": "1",
                          "offset": 0,
                          "length": 1,
                          "children": []
                        }
                      ]
                    }
                  ],
                  "diagnostics": [
                    "ok"
                  ]
                }
                """);

        assertEquals("phase1", result.getBridgeVersion());
        assertEquals("nas-5gs", result.getProtocolName());
        assertEquals(65, result.getMessageType());
        assertEquals("Registration request", result.getMessageTypeName());
        assertEquals("1", result.getFlatFields().get("nas-5gs.mm.5gs_reg_type"));
        assertEquals(1, result.getFieldTree().size());
        assertEquals("Registration request", result.getFieldTree().get(0).getName());
        assertEquals("nas-5gs.mm.5gs_reg_type", result.getFieldTree().get(0).getChildren().get(0).getName());
        assertEquals("ok", result.getDiagnostics().get(0));
    }
}
