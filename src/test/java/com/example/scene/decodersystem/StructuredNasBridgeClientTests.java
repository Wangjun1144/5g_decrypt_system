package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasRequest;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkNasResult;
import com.example.procedure.infrastructure.decode.nativews.NativeWiresharkResultMapper;
import com.example.procedure.infrastructure.decode.nativews.StructuredNasBridgeClient;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDissector;
import com.example.procedure.infrastructure.dissection.nas.message.AuthenticationRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.AuthenticationResponseNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.IdentityRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.IdentityResponseNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.RegistrationCompleteNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.RegistrationRequestNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.SecurityModeCommandNasMessageDissector;
import com.example.procedure.infrastructure.dissection.nas.message.SecurityModeCompleteNasMessageDissector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StructuredNasBridgeClientTests {

    @Test
    void should_decode_registration_request_into_bridge_json_shape() {
        StructuredNasBridgeClient client = new StructuredNasBridgeClient(
                new Nas5gsStructuredDissector(List.of(
                        new RegistrationRequestNasMessageDissector(),
                        new RegistrationCompleteNasMessageDissector(),
                        new IdentityRequestNasMessageDissector(),
                        new IdentityResponseNasMessageDissector(),
                        new AuthenticationRequestNasMessageDissector(),
                        new AuthenticationResponseNasMessageDissector(),
                        new SecurityModeCommandNasMessageDissector(),
                        new SecurityModeCompleteNasMessageDissector()
                )),
                new ObjectMapper()
        );

        NativeWiresharkNasResult result = new NativeWiresharkResultMapper(new ObjectMapper()).mapNas5gs(
                client.decodeNas5gs(new NativeWiresharkNasRequest(
                        new byte[]{0x7e, 0x00, 0x41, 0x01},
                        true,
                        true
                ))
        );

        assertEquals("phase1-java-structured", result.getBridgeVersion());
        assertEquals("nas-5gs", result.getProtocolName());
        assertEquals(0x41, result.getMessageType());
        assertEquals("Registration request", result.getMessageTypeName());
        assertEquals("1", result.getFlatFields().get("nas-5gs.mm.5gs_reg_type"));
        assertFalse(result.getFieldTree().isEmpty());
    }
}
