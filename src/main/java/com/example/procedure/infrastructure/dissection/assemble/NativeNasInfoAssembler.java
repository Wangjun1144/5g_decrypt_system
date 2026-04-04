package com.example.procedure.infrastructure.dissection.assemble;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.model.message.info.NasInfo;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Assembles the native NAS dissection output into the project's existing
 * {@link NasInfo} model.
 *
 * <p>The field semantics are aligned with Wireshark's nas-5gs dissector, while
 * the resulting model remains isolated from the current tshark-based main path.
 */
@Component
public class NativeNasInfoAssembler {

    public NasInfo assemble(CapturedPacket packet, DissectionResult result) {
        if (packet == null) {
            throw new IllegalArgumentException("packet must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (!"nas-5gs".equals(result.getEntryProtocol())) {
            throw new IllegalArgumentException("result is not a NAS-5GS dissection: " + result.getEntryProtocol());
        }

        Map<String, String> fields = result.getDecodedFields();
        NasInfo nas = new NasInfo();
        nas.setSequence((int) packet.getPacketIndex());
        nas.setFullNasPduHex(toHex(packet.getRawBytes()));
        nas.setOriginalFullNasPduHex(nas.getFullNasPduHex());

        nas.setEpd(toHexPrefixed(fields.get("nas-5gs.epd")));
        nas.setSpareHalfOctet(fields.get("nas-5gs.spare_half_octet"));
        nas.setSecurityHeaderType(fields.get("nas-5gs.security_header_type"));
        nas.setMsgAuthCodeHex(toHexPrefixedFixedWidth(fields.get("nas-5gs.msg_auth_code"), 8));
        nas.setSeqNo(fields.get("nas-5gs.seq_no"));
        nas.setMmMessageType(toHexPrefixed(fields.get("nas-5gs.mm.message_type")));
        nas.setNas_cipheringAlgorithm(fields.get("nas-5gs.mm.nas_sec_algo_enc"));
        nas.setNas_integrityProtAlgorithm(fields.get("nas-5gs.mm.nas_sec_algo_ip"));
        nas.setRegType5gs(fields.get("nas-5gs.mm.5gs_reg_type"));
        nas.setGuamiMcc(firstNonBlank(fields.get("e212.guami.mcc"), fields.get("e212.mcc")));
        nas.setGuamiMnc(firstNonBlank(fields.get("e212.guami.mnc"), fields.get("e212.mnc")));
        nas.setTmsi(firstNonBlank(fields.get("nas-5gs.5g_tmsi"), fields.get("nas-5gs.mm.suci.msin")));
        nas.setEncrypted(isEncrypted(fields.get("nas-5gs.security_header_type")));

        Map<String, String> fieldPaths = new LinkedHashMap<>();
        for (String key : fields.keySet()) {
            fieldPaths.put(key, key);
        }
        nas.setFieldPaths(fieldPaths);
        return nas;
    }

    private boolean isEncrypted(String securityHeaderType) {
        if (securityHeaderType == null || securityHeaderType.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(securityHeaderType) != 0;
        } catch (NumberFormatException ignored) {
            return !"0".equals(securityHeaderType.trim());
        }
    }

    private String toHexPrefixed(String numericValue) {
        if (numericValue == null || numericValue.isBlank()) {
            return null;
        }
        try {
            return "0x" + Integer.toHexString(Integer.parseInt(numericValue)).toLowerCase(Locale.ROOT);
        } catch (NumberFormatException ignored) {
            String normalized = numericValue.trim().toLowerCase(Locale.ROOT);
            return normalized.startsWith("0x") ? normalized : "0x" + normalized;
        }
    }

    private String toHexPrefixedFixedWidth(String hexValue, int minWidth) {
        if (hexValue == null || hexValue.isBlank()) {
            return null;
        }
        String normalized = hexValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() >= minWidth) {
            return "0x" + normalized;
        }
        return "0x" + "0".repeat(minWidth - normalized.length()) + normalized;
    }

    private String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
