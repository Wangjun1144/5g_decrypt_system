package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.assemble.NativeNasInfoAssembler;
import com.example.procedure.infrastructure.dissection.assemble.NativeNasSignalingMessageAssembler;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.NasInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeNasAssemblerTests {

    @Test
    void should_assemble_native_nas_info_from_dissection_fields() {
        NativeNasInfoAssembler assembler = new NativeNasInfoAssembler();
        CapturedPacket packet = new CapturedPacket(
                7L,
                Instant.parse("2026-04-01T08:00:00Z"),
                151,
                12,
                12,
                new byte[]{
                        0x7e, 0x04,
                        0x11, 0x22, 0x33, 0x44,
                        0x09,
                        0x7e, 0x00, 0x5d,
                        0x12,
                        0x02
                },
                Path.of("native-security-mode-command.pcap"),
                128L
        );
        DissectionResult result = DissectionResult.of(
                "nas-5gs",
                "NAS-5GS",
                "5G NAS",
                List.of("frame", "nas-5gs"),
                Map.of(
                        "nas-5gs.epd", "126",
                        "nas-5gs.spare_half_octet", "0",
                        "nas-5gs.security_header_type", "4",
                        "nas-5gs.msg_auth_code", "11223344",
                        "nas-5gs.seq_no", "9",
                        "nas-5gs.mm.message_type", "93",
                        "nas-5gs.mm.message_type_name", "Security mode command",
                        "nas-5gs.mm.nas_sec_algo_enc", "1",
                        "nas-5gs.mm.nas_sec_algo_ip", "2"
                )
        );

        NasInfo nas = assembler.assemble(packet, result);

        assertEquals(7, nas.getSequence());
        assertEquals("7e0411223344097e005d1202", nas.getFullNasPduHex());
        assertEquals("7e0411223344097e005d1202", nas.getOriginalFullNasPduHex());
        assertEquals("0x7e", nas.getEpd());
        assertEquals("4", nas.getSecurityHeaderType());
        assertEquals("0x11223344", nas.getMsgAuthCodeHex());
        assertEquals("9", nas.getSeqNo());
        assertEquals("0x5d", nas.getMmMessageType());
        assertEquals("1", nas.getNas_cipheringAlgorithm());
        assertEquals("2", nas.getNas_integrityProtAlgorithm());
        assertTrue(nas.isEncrypted());
        assertEquals("nas-5gs.mm.message_type", nas.getFieldPaths().get("nas-5gs.mm.message_type"));
    }

    @Test
    void should_assemble_native_signaling_message_with_nas_payload() {
        NativeNasSignalingMessageAssembler assembler =
                new NativeNasSignalingMessageAssembler(new NativeNasInfoAssembler());
        CapturedPacket packet = new CapturedPacket(
                3L,
                Instant.parse("2026-04-01T08:00:05Z"),
                151,
                4,
                4,
                new byte[]{0x7e, 0x00, 0x41, 0x01},
                Path.of("native-registration-request.pcap"),
                64L
        );
        DissectionResult result = DissectionResult.of(
                "nas-5gs",
                "NAS-5GS",
                "5G NAS",
                List.of("frame", "nas-5gs"),
                Map.of(
                        "nas-5gs.epd", "126",
                        "nas-5gs.spare_half_octet", "0",
                        "nas-5gs.security_header_type", "0",
                        "nas-5gs.mm.message_type", "65",
                        "nas-5gs.mm.message_type_name", "Registration request",
                        "nas-5gs.mm.5gs_reg_type", "1",
                        "e212.guami.mcc", "001",
                        "e212.guami.mnc", "01",
                        "nas-5gs.5g_tmsi", "c00007ec"
                )
        );

        SignalingMessage message = assembler.assemble(packet, result);

        assertEquals("NATIVE-FRAME-3", message.getMsgId());
        assertEquals(3L, message.getFrameNo());
        assertEquals(1775030405000L, message.getTimestamp());
        assertEquals("Uu", message.getIface());
        assertEquals("NAS", message.getProtocolLayer());
        assertEquals("Registration request", message.getMsgType());
        assertEquals(Boolean.FALSE, message.getEncrypted());
        assertEquals("NONE", message.getEncryptedType());
        assertNotNull(message.getNasList());
        assertEquals(1, message.getNasList().size());
        assertEquals("0x41", message.getNasList().get(0).getMmMessageType());
        assertEquals("1", message.getNasList().get(0).getRegType5gs());
        assertEquals("001", message.getNasList().get(0).getGuamiMcc());
        assertEquals("01", message.getNasList().get(0).getGuamiMnc());
        assertEquals("c00007ec", message.getNasList().get(0).getTmsi());
        assertFalse(message.getNasList().get(0).isEncrypted());
    }
}
