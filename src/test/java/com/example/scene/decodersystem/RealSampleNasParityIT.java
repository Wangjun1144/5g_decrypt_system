package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Replays real NAS payloads previously decoded by Wireshark and checks that the
 * new native NAS entry dissector produces the same first-stage fields.
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "decode.capture-build.mode=native",
                "decode.capture-build.fallback-to-external=false"
        }
)
class RealSampleNasParityIT {

    private static final Path GNB_RAW_JSON = Path.of("gnb_capture_raw.json");
    private static final Path PCAP_DECODE_JSON = Path.of("pcap_decode.json");
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "nas-5gs.epd",
            "nas-5gs.security_header_type",
            "nas-5gs.seq_no",
            "nas-5gs.mm.message_type",
            "nas-5gs.mm.5gs_reg_type",
            "nas-5gs.mm.type_id",
            "nas-5gs.mm.suci.supi_fmt",
            "nas-5gs.mm.tsc",
            "nas-5gs.mm.nas_key_set_id",
            "nas-5gs.mm.tsc.h1",
            "nas-5gs.mm.nas_key_set_id.h1",
            "nas-eps.emm.elem_id",
            "gsm_a.len",
            "gsm_a.dtap.elem_id",
            "e212.guami.mcc",
            "e212.guami.mnc",
            "nas-5gs.amf_region_id",
            "nas-5gs.amf_set_id",
            "nas-5gs.amf_pointer",
            "nas-5gs.5g_tmsi",
            "nas-5gs.mm.suci.scheme_id",
            "nas-5gs.mm.suci.pki",
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
            "nas-5gs.mm.5g_128_ia4"
    );

    @Autowired
    private NativePcapBuildTool nativePcapBuildTool;

    @Autowired
    private PcapFileReader pcapFileReader;

    @Autowired
    private FrameDissector frameDissector;

    @Autowired
    private HexCodec hexCodec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_match_real_sample_registration_request_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(GNB_RAW_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x41");
        assertNotNull(sample, "should find Registration Request sample in gnb_capture_raw.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_reg_request");
        assertExpectedSubset(result, sample.expectedFields());
    }

    @Test
    void should_match_real_sample_security_mode_command_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(PCAP_DECODE_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x5d");
        assertNotNull(sample, "should find Security Mode Command sample in pcap_decode.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_security_mode_command");
        assertExpectedSubset(result, sample.expectedFields());
    }

    @Test
    void should_match_real_sample_authentication_request_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(PCAP_DECODE_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x56");
        assertNotNull(sample, "should find Authentication Request sample in pcap_decode.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_authentication_request");
        assertExpectedSubset(result, sample.expectedFields());
    }

    @Test
    void should_match_real_sample_authentication_response_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(PCAP_DECODE_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x57");
        assertNotNull(sample, "should find Authentication Response sample in pcap_decode.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_authentication_response");
        assertExpectedSubset(result, sample.expectedFields());
    }

    @Test
    void should_match_real_sample_identity_request_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(PCAP_DECODE_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x5b");
        assertNotNull(sample, "should find Identity Request sample in pcap_decode.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_identity_request");
        assertExpectedSubset(result, sample.expectedFields());
    }

    @Test
    void should_match_real_sample_identity_response_from_wireshark_json() throws Exception {
        JsonNode root = readJsonFile(PCAP_DECODE_JSON);
        NasExpectation sample = findFirstNasSampleByMessageType(root, "0x5c");
        assertNotNull(sample, "should find Identity Response sample in pcap_decode.json");

        DissectionResult result = replayNasPayload(sample.fullNasHex(), "real_identity_response");
        assertExpectedSubset(result, sample.expectedFields());
    }

    private DissectionResult replayNasPayload(String fullNasHex, String baseName) throws Exception {
        Path dir = Path.of("runtime", "nas_real_sample_parity");
        Files.createDirectories(dir);
        Path hexdump = dir.resolve(baseName + ".txt");
        Path pcap = dir.resolve(baseName + ".pcap");

        Files.writeString(
                hexdump,
                hexCodec.toText2PcapHexdump(hexCodec.decodeHex(fullNasHex)),
                StandardCharsets.US_ASCII
        );
        nativePcapBuildTool.buildPcap(hexdump, 151, pcap);

        List<CapturedPacket> packets = pcapFileReader.readAll(pcap);
        assertFalse(packets.isEmpty(), "replayed pcap should contain one packet");
        return frameDissector.dissect(packets.get(0));
    }

    private void assertExpectedSubset(DissectionResult result, Map<String, String> expectedFields) {
        for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
            String actual = result.getDecodedFields().get(entry.getKey());
            assertNotNull(actual, "native result should contain " + entry.getKey());
            assertEquals(
                    normalize(entry.getKey(), entry.getValue()),
                    normalize(entry.getKey(), actual),
                    "field mismatch for " + entry.getKey()
            );
        }
    }

    private NasExpectation findFirstNasSampleByMessageType(JsonNode node, String targetMessageTypeHex) {
        List<NasExpectation> matches = new ArrayList<>();
        collectNasSamples(node, normalize("nas-5gs.mm.message_type", targetMessageTypeHex), matches);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void collectNasSamples(JsonNode node, String targetMessageTypeHex, List<NasExpectation> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            NasExpectation sample = toNasExpectation(node, targetMessageTypeHex);
            if (sample != null) {
                out.add(sample);
                return;
            }
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                collectNasSamples(values.next(), targetMessageTypeHex, out);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectNasSamples(child, targetMessageTypeHex, out);
            }
        }
    }

    private NasExpectation toNasExpectation(JsonNode node, String targetMessageTypeHex) {
        JsonNode rawArray = node.get("nas-5gs_raw");
        if (rawArray == null || !rawArray.isArray() || rawArray.isEmpty()) {
            return null;
        }

        String fullNasHex = normalizeHexString(rawArray.get(0).asText(null));
        if (fullNasHex == null || fullNasHex.isBlank()) {
            return null;
        }

        JsonNode nasRoot = node.get("nas-5gs");
        if (nasRoot == null || !nasRoot.isObject()) {
            return null;
        }

        JsonNode secureNode = nasRoot.get("Security protected NAS 5GS message");
        JsonNode plainNode = nasRoot.get("Plain NAS 5GS Message");
        String messageType = extractValue(plainNode, "nas-5gs.mm.message_type");
        if (messageType == null) {
            messageType = extractValue(secureNode, "nas-5gs.mm.message_type");
        }
        if (!targetMessageTypeHex.equals(normalize("nas-5gs.mm.message_type", messageType))) {
            return null;
        }

        java.util.LinkedHashMap<String, String> expected = new java.util.LinkedHashMap<>();
        String epd = extractValue(secureNode, "nas-5gs.epd");
        if (epd == null) {
            epd = extractValue(plainNode, "nas-5gs.epd");
        }
        putIfPresent(expected, "nas-5gs.epd", epd);
        putIfPresent(expected, "nas-5gs.security_header_type", extractValue(secureNode, "nas-5gs.security_header_type"));
        putIfPresent(expected, "nas-5gs.msg_auth_code", extractValue(secureNode, "nas-5gs.msg_auth_code"));
        putIfPresent(expected, "nas-5gs.seq_no", extractValue(secureNode, "nas-5gs.seq_no"));
        putIfPresent(expected, "nas-5gs.mm.message_type", messageType);

        String normalizedMessageType = normalize("nas-5gs.mm.message_type", messageType);
        if ("65".equals(normalizedMessageType)) {
            putIfPresent(expected, "nas-5gs.mm.5gs_reg_type", extractValue(plainNode, "nas-5gs.mm.5gs_reg_type"));
            putIfPresent(expected, "nas-5gs.mm.tsc.h1", extractValue(plainNode, "nas-5gs.mm.tsc.h1"));
            putIfPresent(expected, "nas-5gs.mm.nas_key_set_id.h1", extractValue(plainNode, "nas-5gs.mm.nas_key_set_id.h1"));
            putIfPresent(expected, "gsm_a.len", extractValue(plainNode, "gsm_a.len"));
            putIfPresent(expected, "nas-5gs.mm.type_id", extractValue(plainNode, "nas-5gs.mm.type_id"));
            putIfPresent(expected, "e212.guami.mcc", extractValue(plainNode, "e212.guami.mcc"));
            putIfPresent(expected, "e212.guami.mnc", extractValue(plainNode, "e212.guami.mnc"));
            putIfPresent(expected, "nas-5gs.amf_region_id", extractValue(plainNode, "nas-5gs.amf_region_id"));
            putIfPresent(expected, "nas-5gs.amf_set_id", extractValue(plainNode, "nas-5gs.amf_set_id"));
            putIfPresent(expected, "nas-5gs.amf_pointer", extractValue(plainNode, "nas-5gs.amf_pointer"));
            putIfPresent(expected, "nas-5gs.5g_tmsi", extractValue(plainNode, "nas-5gs.5g_tmsi"));
        } else if ("86".equals(normalizedMessageType)) {
            putIfPresent(expected, "nas-5gs.mm.tsc", extractValue(plainNode, "nas-5gs.mm.tsc"));
            putIfPresent(expected, "nas-5gs.mm.nas_key_set_id", extractValue(plainNode, "nas-5gs.mm.nas_key_set_id"));
            putIfPresent(expected, "gsm_a.len", extractValue(plainNode, "gsm_a.len"));
            putIfPresent(expected, "nas-5gs.mm.abba_contents", extractValue(plainNode, "nas-5gs.mm.abba_contents"));
            putIfPresent(expected, "gsm_a.dtap.elem_id", extractValue(plainNode, "gsm_a.dtap.elem_id"));
            putIfPresent(expected, "gsm_a.dtap.rand", extractValue(plainNode, "gsm_a.dtap.rand"));
            putIfPresent(expected, "gsm_a.dtap.autn", extractValue(plainNode, "gsm_a.dtap.autn"));
            putIfPresent(expected, "gsm_a.dtap.autn.sqn_xor_ak", extractValue(plainNode, "gsm_a.dtap.autn.sqn_xor_ak"));
            putIfPresent(expected, "gsm_a.dtap.autn.amf", extractValue(plainNode, "gsm_a.dtap.autn.amf"));
            putIfPresent(expected, "gsm_a.dtap.autn.mac", extractValue(plainNode, "gsm_a.dtap.autn.mac"));
        } else if ("87".equals(normalizedMessageType)) {
            putIfPresent(expected, "nas-eps.emm.elem_id", extractValue(plainNode, "nas-eps.emm.elem_id"));
            putIfPresent(expected, "gsm_a.len", extractValue(plainNode, "gsm_a.len"));
            putIfPresent(expected, "nas-eps.emm.res", extractValue(plainNode, "nas-eps.emm.res"));
        } else if ("91".equals(normalizedMessageType)) {
            putIfPresent(expected, "nas-5gs.mm.type_id", extractValue(plainNode, "nas-5gs.mm.type_id"));
        } else if ("92".equals(normalizedMessageType)) {
            putIfPresent(expected, "gsm_a.len", extractValue(plainNode, "gsm_a.len"));
            putIfPresent(expected, "nas-5gs.spare_b7", extractValue(plainNode, "nas-5gs.spare_b7"));
            putIfPresent(expected, "nas-5gs.mm.suci.supi_fmt", extractValue(plainNode, "nas-5gs.mm.suci.supi_fmt"));
            putIfPresent(expected, "nas-5gs.spare_b3", extractValue(plainNode, "nas-5gs.spare_b3"));
            putIfPresent(expected, "nas-5gs.mm.type_id", extractValue(plainNode, "nas-5gs.mm.type_id"));
            putIfPresent(expected, "e212.mcc", extractValue(plainNode, "e212.mcc"));
            putIfPresent(expected, "e212.mnc", extractValue(plainNode, "e212.mnc"));
            putIfPresent(expected, "nas-5gs.mm.suci.routing_indicator", extractValue(plainNode, "nas-5gs.mm.suci.routing_indicator"));
            putIfPresent(expected, "nas-5gs.mm.suci.scheme_id", extractValue(plainNode, "nas-5gs.mm.suci.scheme_id"));
            putIfPresent(expected, "nas-5gs.mm.suci.pki", extractValue(plainNode, "nas-5gs.mm.suci.pki"));
            putIfPresent(expected, "nas-5gs.mm.suci.msin", extractValue(plainNode, "nas-5gs.mm.suci.msin"));
        } else if ("93".equals(normalizedMessageType)) {
            putIfPresent(expected, "nas-5gs.mm.tsc", extractValue(plainNode, "nas-5gs.mm.tsc"));
            putIfPresent(expected, "nas-5gs.mm.nas_key_set_id", extractValue(plainNode, "nas-5gs.mm.nas_key_set_id"));
            putIfPresent(expected, "nas-5gs.mm.nas_sec_algo_enc", extractValue(plainNode, "nas-5gs.mm.nas_sec_algo_enc"));
            putIfPresent(expected, "nas-5gs.mm.nas_sec_algo_ip", extractValue(plainNode, "nas-5gs.mm.nas_sec_algo_ip"));
            putIfPresent(expected, "gsm_a.len", extractValue(plainNode, "gsm_a.len"));
            putIfPresent(expected, "nas-5gs.mm.5g_ea0", extractValue(plainNode, "nas-5gs.mm.5g_ea0"));
            putIfPresent(expected, "nas-5gs.mm.128_5g_ea1", extractValue(plainNode, "nas-5gs.mm.128_5g_ea1"));
            putIfPresent(expected, "nas-5gs.mm.128_5g_ea2", extractValue(plainNode, "nas-5gs.mm.128_5g_ea2"));
            putIfPresent(expected, "nas-5gs.mm.128_5g_ea3", extractValue(plainNode, "nas-5gs.mm.128_5g_ea3"));
            putIfPresent(expected, "nas-5gs.mm.5g_ea4", extractValue(plainNode, "nas-5gs.mm.5g_ea4"));
            putIfPresent(expected, "nas-5gs.mm.5g_ea5", extractValue(plainNode, "nas-5gs.mm.5g_ea5"));
            putIfPresent(expected, "nas-5gs.mm.5g_ea6", extractValue(plainNode, "nas-5gs.mm.5g_ea6"));
            putIfPresent(expected, "nas-5gs.mm.5g_ea7", extractValue(plainNode, "nas-5gs.mm.5g_ea7"));
            putIfPresent(expected, "nas-5gs.mm.ia0", extractValue(plainNode, "nas-5gs.mm.ia0"));
            putIfPresent(expected, "nas-5gs.mm.5g_128_ia1", extractValue(plainNode, "nas-5gs.mm.5g_128_ia1"));
            putIfPresent(expected, "nas-5gs.mm.5g_128_ia2", extractValue(plainNode, "nas-5gs.mm.5g_128_ia2"));
            putIfPresent(expected, "nas-5gs.mm.5g_128_ia3", extractValue(plainNode, "nas-5gs.mm.5g_128_ia3"));
            putIfPresent(expected, "nas-5gs.mm.5g_128_ia4", extractValue(plainNode, "nas-5gs.mm.5g_128_ia4"));
        }
        return expected.isEmpty() ? null : new NasExpectation(fullNasHex, Map.copyOf(expected));
    }

    private String extractValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null && direct.isValueNode()) {
                return direct.asText();
            }
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String found = extractValue(values.next(), fieldName);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = extractValue(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private JsonNode readJsonFile(Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return objectMapper.readTree(extractJsonPayload(content));
    }

    private String extractJsonPayload(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON input must not be null");
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
            throw new IllegalStateException("No JSON payload found in input");
        }
        return text.substring(start);
    }

    private String normalize(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        if ("nas-5gs.msg_auth_code".equals(fieldName)
                || "nas-eps.emm.res".equals(fieldName)
                || "nas-5gs.mm.abba_contents".equals(fieldName)
                || "gsm_a.dtap.rand".equals(fieldName)
                || "gsm_a.dtap.autn".equals(fieldName)
                || "gsm_a.dtap.autn.sqn_xor_ak".equals(fieldName)
                || "gsm_a.dtap.autn.amf".equals(fieldName)
                || "gsm_a.dtap.autn.mac".equals(fieldName)) {
            return normalizeHexString(value);
        }
        if ("nas-5gs.5g_tmsi".equals(fieldName)) {
            String normalized = normalizeHexString(value);
            return Long.toString(Long.parseUnsignedLong(normalized, detectFlexibleRadix(value)));
        }
        if (NUMERIC_FIELDS.contains(fieldName)) {
            String normalized = normalizeHexString(value);
            try {
                if (!normalized.isEmpty()) {
                    return Integer.toString(Integer.parseInt(normalized, detectRadix(value)));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return normalizeHexString(value);
    }

    private int detectRadix(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("0x")) {
            return 16;
        }
        return 10;
    }

    private int detectFlexibleRadix(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("0x")) {
            return 16;
        }
        return value.matches(".*[a-f].*") ? 16 : 10;
    }

    private String normalizeHexString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return normalized.replace(":", "").replace(" ", "");
    }

    private record NasExpectation(String fullNasHex, Map<String, String> expectedFields) {
    }
}
