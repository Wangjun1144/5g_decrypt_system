package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import com.example.procedure.infrastructure.decode.bridge.json.PcapJsonDecodeGateway;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.FrameDissector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compares the new native NAS entry dissector with the current tshark-based
 * decode output on a shared synthetic input.
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "decode.capture-build.mode=native",
                "decode.capture-build.fallback-to-external=false"
        }
)
class NasDissectorParityIT {

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "nas-5gs.epd",
            "nas-5gs.security_header_type",
            "nas-5gs.seq_no",
            "nas-5gs.mm.message_type",
            "nas-5gs.mm.5gs_reg_type",
            "nas-5gs.mm.tsc.h1",
            "nas-5gs.mm.nas_key_set_id.h1",
            "nas-5gs.spare_b7",
            "nas-5gs.spare_b6",
            "nas-5gs.spare_b5",
            "nas-5gs.spare_b4",
            "nas-5gs.spare_b3",
            "nas-5gs.mm.type_id",
            "nas-5gs.mm.suci.supi_fmt",
            "nas-5gs.mm.tsc",
            "nas-5gs.mm.nas_key_set_id",
            "nas-eps.emm.elem_id",
            "gsm_a.len",
            "gsm_a.dtap.elem_id",
            "nas-5gs.mm.elem_id",
            "e212.guami.mcc",
            "e212.guami.mnc",
            "nas-5gs.amf_region_id",
            "nas-5gs.amf_set_id",
            "nas-5gs.amf_pointer",
            "nas-5gs.5g_tmsi",
            "3gpp.tmsi",
            "nas-5gs.mm.suci.scheme_id",
            "nas-5gs.mm.suci.pki",
            "nas-5gs.mm.sprti_b1",
            "nas-5gs.mm.raai_b0",
            "nas-5gs.mm.pld_cont_type",
            "nas-5gs.mm.sgc_b7",
            "nas-5gs.mm.5g_iphc_cp_ciot_b6",
            "nas-5gs.mm.n3_data_b5",
            "nas-5gs.mm.5g_cp_ciot_b4",
            "nas-5gs.mm.restrict_ec_b3",
            "nas-5gs.mm.lpp_cap_b2",
            "nas-5gs.mm.ho_attach_b1",
            "nas-5gs.mm.s1_mode_b0",
            "nas-5gs.mm.racs_b7",
            "nas-5gs.mm.nssaa_b6",
            "nas-5gs.mm.5g_lcs_b5",
            "nas-5gs.mm.v2xcnpc5_b4",
            "nas-5gs.mm.v2xcepc5_b3",
            "nas-5gs.mm.v2x_b2",
            "nas-5gs.mm.5g_up_ciot_b1",
            "nas-5gs.mm.5g_srvcc_b0",
            "nas-5gs.mm.prose_l2relay_b7",
            "nas-5gs.mm.prose_dc_b6",
            "nas-5gs.mm.prose_dd_b5",
            "nas-5gs.mm.er_nssai_b4",
            "nas-5gs.mm.ehc_cp_ciot_b3",
            "nas-5gs.mm.multiple_up_b2",
            "nas-5gs.mm.wsusa_b1",
            "nas-5gs.mm.cag_b0",
            "nas-5gs.mm.pr_b7",
            "nas-5gs.mm.rpr_b6",
            "nas-5gs.mm.piv_b5",
            "nas-5gs.mm.ncr_b4",
            "nas-5gs.mm.nr_pssi_b3",
            "nas-5gs.mm.5g_prose_l3rmt_b2",
            "nas-5gs.mm.5g_prose_l2rmt_b1",
            "nas-5gs.mm.5g_prose_l3relay_b0",
            "nas-5gs.mm.mpsiu_b7",
            "nas-5gs.mm.uas_b6",
            "nas-5gs.mm.nsag_b5",
            "nas-5gs.mm.ex_cag_b4",
            "nas-5gs.mm.ssnpnsi_b3",
            "nas-5gs.mm.event_notif_b2",
            "nas-5gs.mm.mint_b1",
            "nas-5gs.mm.nssrg_b0",
            "nas-5gs.mm.sbts_b7",
            "nas-5gs.mm.nsr_b6",
            "nas-5gs.mm.ladn_ds_b5",
            "nas-5gs.mm.rantiming_b4",
            "nas-5gs.mm.eci_b3",
            "nas-5gs.mm.esi_b2",
            "nas-5gs.mm.rcman_b1",
            "nas-5gs.mm.rcmap_b0",
            "nas-5gs.mm.nas_sec_algo_enc",
            "nas-5gs.mm.nas_sec_algo_ip",
            "nas-5gs.mm.5g_ea0",
            "nas-5gs.mm.128_5g_ea1",
            "nas-5gs.mm.128_5g_ea2",
            "nas-5gs.mm.128_5g_ea3",
            "nas-5gs.mm.5g_ea4",
            "nas-5gs.mm.5g_ea5",
            "nas-5gs.mm.5g_ea6",
            "nas-5gs.mm.5g_ea7",
            "nas-5gs.mm.ia0",
            "nas-5gs.mm.5g_128_ia1",
            "nas-5gs.mm.5g_128_ia2",
            "nas-5gs.mm.5g_128_ia3",
            "nas-5gs.mm.5g_128_ia4",
            "nas-5gs.mm.5g_ia5",
            "nas-5gs.mm.5g_ia6",
            "nas-5gs.mm.5g_ia7"
    );

    @Autowired
    private NativePcapBuildTool nativePcapBuildTool;

    @Autowired
    private PcapFileReader pcapFileReader;

    @Autowired
    private FrameDissector frameDissector;

    @Autowired
    private PcapJsonDecodeGateway pcapJsonDecodeGateway;

    @Autowired
    private HexCodec hexCodec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registration_request_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap("7e004101000bf200f110020040c00007ec2e04f0700000", "nas_registration_request");
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5gs_reg_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.tsc.h1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nas_key_set_id.h1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b3");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.len");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.type_id");
        assertFieldParity(nativeResult, tsharkPacket, "e212.guami.mcc");
        assertFieldParity(nativeResult, tsharkPacket, "e212.guami.mnc");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.amf_region_id");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.amf_set_id");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.amf_pointer");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.5g_tmsi");
        assertFieldParity(nativeResult, tsharkPacket, "3gpp.tmsi");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.elem_id");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea2");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea3");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.ia0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia2");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia3");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia7");
    }

    @Test
    void registration_request_optional_ie_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap(
                "7e004101000bf200f110020040c00007ecb18377000bf200f110020040c00007ec",
                "nas_registration_request_optionals"
        );
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.sprti_b1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.raai_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.pld_cont_type");
    }

    @Test
    void registration_request_5gmm_capability_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap(
                "7e004101000bf200f110020040c00007ec1006a55ac33cf00f",
                "nas_registration_request_5gmm_cap"
        );
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.sgc_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_iphc_cp_ciot_b6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.n3_data_b5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_cp_ciot_b4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.s1_mode_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.racs_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nssaa_b6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_srvcc_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.prose_l2relay_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.cag_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.pr_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_prose_l3relay_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.mpsiu_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nssrg_b0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.sbts_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.rcmap_b0");
    }

    @Test
    void security_mode_command_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap("7e04a1b2c3d4097e005d120204f0700000", "nas_security_mode_command");
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.msg_auth_code");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.seq_no");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.tsc");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nas_key_set_id");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nas_sec_algo_enc");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nas_sec_algo_ip");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.len");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea2");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.128_5g_ea3");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ea7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.ia0");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia1");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia2");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia3");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_128_ia4");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia5");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia6");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.5g_ia7");
    }

    @Test
    void authentication_request_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap(
                "7e005602020000212fec6ee66bcde5d6a5966c9ca0ca80db201030caab0a5e5180007e410f6db28e2866",
                "nas_authentication_request"
        );
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.tsc");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.nas_key_set_id");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.len");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.abba_contents");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.elem_id");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.rand");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.autn");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.autn.sqn_xor_ak");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.autn.amf");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.dtap.autn.mac");
    }

    @Test
    void authentication_response_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap(
                "7e00572d10f412b499c371bf6aacf043d819b191d8",
                "nas_authentication_response"
        );
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-eps.emm.elem_id");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.len");
        assertFieldParity(nativeResult, tsharkPacket, "nas-eps.emm.res");
    }

    @Test
    void identity_request_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap("7e005b01", "nas_identity_request");
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.type_id");
    }

    @Test
    void identity_response_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap(
                "7e005c000d0100f110f0ff00000000000010",
                "nas_identity_response"
        );
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
        assertFieldParity(nativeResult, tsharkPacket, "gsm_a.len");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b7");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.suci.supi_fmt");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.spare_b3");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.type_id");
        assertFieldParity(nativeResult, tsharkPacket, "e212.mcc");
        assertFieldParity(nativeResult, tsharkPacket, "e212.mnc");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.suci.routing_indicator");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.suci.scheme_id");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.suci.pki");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.suci.msin");
    }

    @Test
    void registration_complete_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap("7e0045", "nas_registration_complete");
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
    }

    @Test
    void security_mode_complete_fields_should_match_tshark_output() throws Exception {
        Path pcap = buildNasPcap("7e005e", "nas_security_mode_complete");
        DissectionResult nativeResult = dissectNative(pcap);
        JsonNode tsharkPacket = decodeViaTshark(pcap);

        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.epd");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.security_header_type");
        assertFieldParity(nativeResult, tsharkPacket, "nas-5gs.mm.message_type");
    }

    private Path buildNasPcap(String plainHex, String baseName) throws Exception {
        Path dir = Path.of("runtime", "nas_parity");
        Files.createDirectories(dir);
        Path hexdump = dir.resolve(baseName + ".txt");
        Path pcap = dir.resolve(baseName + ".pcap");
        Files.writeString(
                hexdump,
                hexCodec.toText2PcapHexdump(hexCodec.decodeHex(plainHex)),
                StandardCharsets.US_ASCII
        );
        nativePcapBuildTool.buildPcap(hexdump, 151, pcap);
        return pcap;
    }

    private DissectionResult dissectNative(Path pcap) throws Exception {
        List<CapturedPacket> packets = pcapFileReader.readAll(pcap);
        assertFalse(packets.isEmpty(), "pcap should contain one packet");
        return frameDissector.dissect(packets.get(0));
    }

    private JsonNode decodeViaTshark(Path pcap) throws Exception {
        String json = pcapJsonDecodeGateway.decodeToJson(pcap);
        JsonNode root = objectMapper.readTree(extractJsonPayload(json));
        assertNotNull(root);
        JsonNode packet = root.isArray() ? root.path(0) : root;
        assertFalse(packet.isMissingNode(), "tshark should return at least one packet");
        return packet;
    }

    private String extractJsonPayload(String text) {
        if (text == null) {
            throw new IllegalArgumentException("tshark output must not be null");
        }
        int arrayStart = text.indexOf('[');
        int objectStart = text.indexOf('{');
        int start;
        if (arrayStart < 0) {
            start = objectStart;
        } else if (objectStart < 0) {
            start = arrayStart;
        } else {
            start = Math.min(arrayStart, objectStart);
        }
        if (start < 0) {
            throw new IllegalStateException("No JSON payload found in tshark output: " + text);
        }
        return text.substring(start);
    }

    private void assertFieldParity(DissectionResult nativeResult, JsonNode tsharkPacket, String fieldName) {
        String nativeValue = nativeResult.getDecodedFields().get(fieldName);
        String tsharkValue = findFieldValue(tsharkPacket, fieldName);
        assertNotNull(nativeValue, "native result should contain " + fieldName);
        assertNotNull(tsharkValue, "tshark result should contain " + fieldName);
        assertEquals(
                normalizeFieldValue(fieldName, tsharkValue),
                normalizeFieldValue(fieldName, nativeValue),
                "field mismatch for " + fieldName
        );
    }

    private String findFieldValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null && direct.isValueNode()) {
                return direct.asText();
            }
            for (JsonNode child : node) {
                String found = findFieldValue(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFieldValue(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String normalizeFieldValue(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("0x")) {
            trimmed = trimmed.substring(2);
        }

        if ("nas-5gs.msg_auth_code".equals(fieldName)
                || "nas-eps.emm.res".equals(fieldName)
                || "nas-5gs.mm.abba_contents".equals(fieldName)
                || "gsm_a.dtap.rand".equals(fieldName)
                || "gsm_a.dtap.autn".equals(fieldName)
                || "gsm_a.dtap.autn.sqn_xor_ak".equals(fieldName)
                || "gsm_a.dtap.autn.amf".equals(fieldName)
                || "gsm_a.dtap.autn.mac".equals(fieldName)) {
            return trimmed.replace(":", "").replace(" ", "");
        }

        if ("nas-5gs.5g_tmsi".equals(fieldName) || "3gpp.tmsi".equals(fieldName)) {
            return Long.toString(Long.parseUnsignedLong(trimmed, detectFlexibleRadix(value)));
        }

        if (NUMERIC_FIELDS.contains(fieldName)) {
            return Integer.toString(Integer.parseInt(trimmed, detectRadix(value)));
        }

        return trimmed;
    }

    private int detectRadix(String rawValue) {
        String v = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("0x")) {
            return 16;
        }
        return 10;
    }

    private int detectFlexibleRadix(String rawValue) {
        String v = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("0x")) {
            return 16;
        }
        return v.matches(".*[a-f].*") ? 16 : 10;
    }
}
