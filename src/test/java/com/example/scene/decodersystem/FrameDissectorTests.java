package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.FrameDissector;
import com.example.procedure.infrastructure.dissection.registry.LinkTypeDissectorRegistry;
import com.example.procedure.infrastructure.dissection.registry.ProtocolDissectorRegistry;
import com.example.procedure.infrastructure.dissection.entry.Nas5gsEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NgapEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcDlCcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcDlDcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcUlCcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcUlDcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.UdpEntryDissector;
import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameDissectorTests {

    private ProtocolDissectorRegistry protocolRegistry() {
        return new ProtocolDissectorRegistry(List.of(
                new Nas5gsEntryDissector(),
                new NgapEntryDissector(),
                new UdpEntryDissector(),
                new NrRrcUlDcchEntryDissector(),
                new NrRrcDlDcchEntryDissector(),
                new NrRrcUlCcchEntryDissector(),
                new NrRrcDlCcchEntryDissector()
        ));
    }

    @Test
    void should_dispatch_packet_by_link_type_using_registry() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        Map<Integer, String> userDlts = new LinkedHashMap<>();
        userDlts.put(151, "nas-5gs");
        props.setUserDlts(userDlts);

        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));
        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                4,
                4,
                new byte[]{0x7e, 0x00, 0x5e, 0x01},
                Path.of("dummy.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("nas-5gs", result.getEntryProtocol());
        assertEquals("NAS-5GS", result.getEntryShortName());
        assertEquals("5G NAS", result.getEntryDisplayName());
        assertEquals(List.of("frame", "nas-5gs"), result.getProtocolTrace());
        assertEquals("126", result.getDecodedFields().get("nas-5gs.epd"));
        assertEquals("94", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Security mode complete", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertTrue(!result.getFieldTree().isEmpty());
    }

    @Test
    void should_fail_fast_when_no_entry_dissector_is_registered() {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of());
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));
        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                999,
                1,
                1,
                new byte[]{0x01},
                Path.of("dummy.pcap"),
                40L
        );

        assertThrows(IllegalStateException.class, () -> dissector.dissect(packet));
    }

    @Test
    void should_connect_pcap_reader_output_to_frame_dissector() throws Exception {
        HexCodec hexCodec = new HexCodec();
        NativePcapBuildTool builder = new NativePcapBuildTool();
        PcapFileReader reader = new PcapFileReader();

        byte[] payload = hexCodec.decodeHex("0102030405");
        Path dir = Files.createTempDirectory("frame-dissector");
        Path hexdump = dir.resolve("payload.txt");
        Path pcap = dir.resolve("payload.pcap");
        Files.writeString(hexdump, hexCodec.toText2PcapHexdump(payload), StandardCharsets.US_ASCII);
        builder.buildPcap(hexdump, 151, pcap);

        WiresharkProperties props = new WiresharkProperties();
        Map<Integer, String> userDlts = new LinkedHashMap<>();
        userDlts.put(151, "nas-5gs");
        props.setUserDlts(userDlts);

        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));
        List<CapturedPacket> packets = reader.readAll(pcap);

        assertEquals(1, packets.size());
        DissectionResult result = dissector.dissect(packets.get(0));
        assertEquals("nas-5gs", result.getEntryProtocol());
        assertTrue(result.getProtocolTrace().contains("frame"));
        assertTrue(result.getProtocolTrace().contains("nas-5gs"));
    }

    @Test
    void nas_entry_dissector_should_extract_plain_nas5gs_header_fields() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                4,
                4,
                new byte[]{0x7e, 0x00, 0x41, 0x01},
                Path.of("plain-nas.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("126", result.getDecodedFields().get("nas-5gs.epd"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.spare_half_octet"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.security_header_type"));
        assertEquals("65", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Registration request", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.5gs_reg_type"));
    }

    @Test
    void nas_entry_dissector_should_extract_security_header_fields_and_nested_plain_type() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                10,
                10,
                new byte[]{
                        0x7e, 0x04,
                        (byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4,
                        0x05,
                        0x7e, 0x00, 0x5e
                },
                Path.of("secure-nas.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("4", result.getDecodedFields().get("nas-5gs.security_header_type"));
        assertEquals("a1b2c3d4", result.getDecodedFields().get("nas-5gs.msg_auth_code"));
        assertEquals("5", result.getDecodedFields().get("nas-5gs.seq_no"));
        assertEquals("126", result.getDecodedFields().get("nas-5gs.inner.epd"));
        assertEquals("94", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Security mode complete", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
    }

    @Test
    void nas_entry_dissector_should_extract_security_mode_command_algorithms() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
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
                Path.of("security-mode-command.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("93", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Security mode command", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.nas_sec_algo_enc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_sec_algo_ip"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.tsc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id"));
    }

    @Test
    void nas_entry_dissector_should_extract_authentication_request_ngksi_fields() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                4,
                4,
                new byte[]{0x7e, 0x00, 0x56, 0x02},
                Path.of("authentication-request.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("86", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Authentication request", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.tsc"));
        assertEquals("2", result.getDecodedFields().get("nas-5gs.mm.nas_key_set_id"));
    }

    @Test
    void nas_entry_dissector_should_extract_authentication_response_parameter_fields() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                21,
                21,
                new byte[]{
                        0x7e, 0x00, 0x57, 0x2d, 0x10,
                        (byte) 0xf4, 0x12, (byte) 0xb4, (byte) 0x99,
                        (byte) 0xc3, 0x71, (byte) 0xbf, 0x6a,
                        (byte) 0xac, (byte) 0xf0, 0x43, (byte) 0xd8,
                        0x19, (byte) 0xb1, (byte) 0x91, (byte) 0xd8
                },
                Path.of("authentication-response.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("87", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Authentication response", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("45", result.getDecodedFields().get("nas-eps.emm.elem_id"));
        assertEquals("16", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("f412b499c371bf6aacf043d819b191d8", result.getDecodedFields().get("nas-eps.emm.res"));
    }

    @Test
    void nas_entry_dissector_should_extract_identity_request_fields() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                4,
                4,
                new byte[]{0x7e, 0x00, 0x5b, 0x01},
                Path.of("identity-request.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("91", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Identity request", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.type_id"));
    }

    @Test
    void nas_entry_dissector_should_extract_identity_response_fields() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                18,
                18,
                new byte[]{
                        0x7e, 0x00, 0x5c, 0x00, 0x0d,
                        0x01, 0x00, (byte) 0xf1, 0x10, (byte) 0xf0, (byte) 0xff,
                        0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x10
                },
                Path.of("identity-response.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("92", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Identity response", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
        assertEquals("13", result.getDecodedFields().get("gsm_a.len"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.supi_fmt"));
        assertEquals("1", result.getDecodedFields().get("nas-5gs.mm.type_id"));
        assertEquals("001", result.getDecodedFields().get("e212.mcc"));
        assertEquals("01", result.getDecodedFields().get("e212.mnc"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.routing_indicator"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.scheme_id"));
        assertEquals("0", result.getDecodedFields().get("nas-5gs.mm.suci.pki"));
        assertEquals("0000000001", result.getDecodedFields().get("nas-5gs.mm.suci.msin"));
        assertTrue(!result.getFieldTree().isEmpty());
        assertEquals("nas-5gs", result.getFieldTree().get(0).getName());
    }

    @Test
    void nas_entry_dissector_should_extract_registration_complete_message_identity() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                3,
                3,
                new byte[]{0x7e, 0x00, 0x45},
                Path.of("registration-complete.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("69", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Registration complete", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
    }

    @Test
    void nas_entry_dissector_should_extract_security_mode_complete_message_identity() throws Exception {
        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));
        FrameDissector dissector = new FrameDissector(new LinkTypeDissectorRegistry(protocolRegistry(), props));

        CapturedPacket packet = new CapturedPacket(
                1L,
                Instant.now(),
                151,
                3,
                3,
                new byte[]{0x7e, 0x00, 0x5e},
                Path.of("security-mode-complete.pcap"),
                40L
        );

        DissectionResult result = dissector.dissect(packet);
        assertEquals("94", result.getDecodedFields().get("nas-5gs.mm.message_type"));
        assertEquals("Security mode complete", result.getDecodedFields().get("nas-5gs.mm.message_type_name"));
    }
}
