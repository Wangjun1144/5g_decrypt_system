package com.example.procedure.infrastructure.dissection.entry;

import com.example.procedure.infrastructure.dissection.PacketBuffer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal NAS 5GS header parser aligned with the first fields Wireshark exposes
 * in packet-nas_5gs.c.
 *
 * <p>This parser intentionally only covers the outer security/plain header and
 * selected nested plain-message fields. It does not yet implement the full
 * Wireshark NAS dissector.</p>
 */
final class Nas5gsHeaderParser {

    private Nas5gsHeaderParser() {
    }

    static Map<String, String> parse(PacketBuffer buffer) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (buffer == null || buffer.length() < 2) {
            return fields;
        }

        int epd = buffer.getU8(0);
        int secondOctet = buffer.getU8(1);
        int spareHalfOctet = (secondOctet >>> 4) & 0x0f;
        int securityHeaderType = secondOctet & 0x0f;

        fields.put("nas-5gs.epd", Integer.toString(epd));
        fields.put("nas-5gs.spare_half_octet", Integer.toString(spareHalfOctet));
        fields.put("nas-5gs.security_header_type", Integer.toString(securityHeaderType));

        if (securityHeaderType == 0) {
            if (buffer.length() >= 3) {
                int messageType = buffer.getU8(2);
                fields.put("nas-5gs.mm.message_type", Integer.toString(messageType));
                putMessageName(fields, messageType);
                parsePlainMmSpecificFields(fields, messageType, buffer, 3);
            }
            return fields;
        }

        if (buffer.length() >= 6) {
            fields.put("nas-5gs.msg_auth_code", hex(buffer.slice(2, 4).toByteArray()));
        }
        if (buffer.length() >= 7) {
            fields.put("nas-5gs.seq_no", Integer.toString(buffer.getU8(6)));
        }

        // Mirror Wireshark's common follow-up dissection idea:
        // after the security header (EPD + sec hdr + MAC + seq), a plain NAS
        // message may follow and expose the usual mm.message_type field.
        if (buffer.length() >= 10 && buffer.getU8(7) == 0x7e) {
            fields.put("nas-5gs.inner.epd", Integer.toString(buffer.getU8(7)));
            int innerSecondOctet = buffer.getU8(8);
            fields.put("nas-5gs.inner.spare_half_octet", Integer.toString((innerSecondOctet >>> 4) & 0x0f));
            fields.put("nas-5gs.inner.security_header_type", Integer.toString(innerSecondOctet & 0x0f));
            int innerMessageType = buffer.getU8(9);
            fields.put("nas-5gs.mm.message_type", Integer.toString(innerMessageType));
            putMessageName(fields, innerMessageType);
            parsePlainMmSpecificFields(fields, innerMessageType, buffer, 10);
        }

        return fields;
    }

    private static void parsePlainMmSpecificFields(
            Map<String, String> fields,
            int messageType,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (bodyOffset >= buffer.length()) {
            return;
        }

        switch (messageType) {
            case 0x41 -> parseRegistrationRequest(fields, buffer, bodyOffset);
            case 0x56 -> parseAuthenticationRequest(fields, buffer, bodyOffset);
            case 0x57 -> parseAuthenticationResponse(fields, buffer, bodyOffset);
            case 0x5b -> parseIdentityRequest(fields, buffer, bodyOffset);
            case 0x5c -> parseIdentityResponse(fields, buffer, bodyOffset);
            case 0x5d -> parseSecurityModeCommand(fields, buffer, bodyOffset);
            default -> {
            }
        }
    }

    private static void parseRegistrationRequest(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 1) {
            return;
        }
        int ngKsiAndRegType = buffer.getU8(bodyOffset);
        fields.put("nas-5gs.mm.5gs_reg_type", Integer.toString(ngKsiAndRegType & 0x07));
    }

    private static void parseAuthenticationRequest(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 1) {
            return;
        }
        int ngKsiOctet = buffer.getU8(bodyOffset);
        fields.put("nas-5gs.mm.tsc", Integer.toString((ngKsiOctet >>> 3) & 0x01));
        fields.put("nas-5gs.mm.nas_key_set_id", Integer.toString(ngKsiOctet & 0x07));
    }

    private static void parseSecurityModeCommand(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 1) {
            return;
        }
        int selectedNasSecurityAlgorithms = buffer.getU8(bodyOffset);
        fields.put("nas-5gs.mm.nas_sec_algo_enc", Integer.toString((selectedNasSecurityAlgorithms >>> 4) & 0x0f));
        fields.put("nas-5gs.mm.nas_sec_algo_ip", Integer.toString(selectedNasSecurityAlgorithms & 0x0f));
        if (buffer.remaining(bodyOffset) >= 2) {
            int ngKsiOctet = buffer.getU8(bodyOffset + 1);
            fields.put("nas-5gs.mm.tsc", Integer.toString((ngKsiOctet >>> 3) & 0x01));
            fields.put("nas-5gs.mm.nas_key_set_id", Integer.toString(ngKsiOctet & 0x07));
        }
    }

    private static void parseIdentityRequest(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 1) {
            return;
        }
        int identityTypeOctet = buffer.getU8(bodyOffset);
        fields.put("nas-5gs.mm.type_id", Integer.toString(identityTypeOctet & 0x07));
    }

    private static void parseIdentityResponse(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 3) {
            return;
        }
        int mobileIdentityLength = buffer.getU16(bodyOffset);
        fields.put("gsm_a.len", Integer.toString(mobileIdentityLength));
        if (buffer.remaining(bodyOffset + 2) < mobileIdentityLength) {
            return;
        }

        int identityHeader = buffer.getU8(bodyOffset + 2);
        fields.put("nas-5gs.mm.suci.supi_fmt", Integer.toString((identityHeader >>> 4) & 0x07));
        fields.put("nas-5gs.mm.type_id", Integer.toString(identityHeader & 0x07));

        if (mobileIdentityLength < 13 || buffer.remaining(bodyOffset + 3) < 10) {
            return;
        }

        int plmnOffset = bodyOffset + 3;
        int b1 = buffer.getU8(plmnOffset);
        int b2 = buffer.getU8(plmnOffset + 1);
        int b3 = buffer.getU8(plmnOffset + 2);
        fields.put("e212.mcc", decodeMcc(b1, b2));
        fields.put("e212.mnc", decodeMnc(b2, b3));

        fields.put(
                "nas-5gs.mm.suci.routing_indicator",
                decodeTbcd(buffer.slice(plmnOffset + 3, 2).toByteArray())
        );

        int schemeAndPkiOffset = plmnOffset + 5;
        fields.put("nas-5gs.mm.suci.scheme_id", Integer.toString(buffer.getU8(schemeAndPkiOffset) & 0x0f));
        fields.put("nas-5gs.mm.suci.pki", Integer.toString(buffer.getU8(schemeAndPkiOffset + 1)));
        int msinLength = mobileIdentityLength - 8;
        if (msinLength > 0 && buffer.remaining(schemeAndPkiOffset + 2) >= msinLength) {
            fields.put(
                    "nas-5gs.mm.suci.msin",
                    decodeTbcd(buffer.slice(schemeAndPkiOffset + 2, msinLength).toByteArray())
            );
        }
    }

    private static void parseAuthenticationResponse(
            Map<String, String> fields,
            PacketBuffer buffer,
            int bodyOffset
    ) {
        if (buffer.remaining(bodyOffset) < 2) {
            return;
        }
        int elementId = buffer.getU8(bodyOffset);
        int valueLength = buffer.getU8(bodyOffset + 1);
        fields.put("nas-eps.emm.elem_id", Integer.toString(elementId));
        fields.put("gsm_a.len", Integer.toString(valueLength));
        if (buffer.remaining(bodyOffset + 2) < valueLength) {
            return;
        }
        fields.put(
                "nas-eps.emm.res",
                hex(buffer.slice(bodyOffset + 2, valueLength).toByteArray())
        );
    }

    private static void putMessageName(Map<String, String> fields, int messageType) {
        String messageName = messageTypeName(messageType);
        if (messageName != null) {
            fields.put("nas-5gs.mm.message_type_name", messageName);
        }
    }

    private static String messageTypeName(int messageType) {
        return switch (messageType) {
            case 0x41 -> "Registration request";
            case 0x45 -> "Registration complete";
            case 0x56 -> "Authentication request";
            case 0x57 -> "Authentication response";
            case 0x5b -> "Identity request";
            case 0x5c -> "Identity response";
            case 0x5d -> "Security mode command";
            case 0x5e -> "Security mode complete";
            default -> null;
        };
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String decodeMcc(int b1, int b2) {
        return new StringBuilder(3)
                .append(b1 & 0x0f)
                .append((b1 >>> 4) & 0x0f)
                .append(b2 & 0x0f)
                .toString();
    }

    private static String decodeMnc(int b2, int b3) {
        int digit3 = (b2 >>> 4) & 0x0f;
        StringBuilder sb = new StringBuilder(3);
        sb.append(b3 & 0x0f);
        sb.append((b3 >>> 4) & 0x0f);
        if (digit3 != 0x0f) {
            sb.append(digit3);
        }
        return sb.toString();
    }

    private static String decodeTbcd(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int low = value & 0x0f;
            int high = (value >>> 4) & 0x0f;
            appendTbcdDigit(sb, low);
            appendTbcdDigit(sb, high);
        }
        return sb.isEmpty() ? "0" : sb.toString();
    }

    private static void appendTbcdDigit(StringBuilder sb, int digit) {
        if (digit <= 9) {
            sb.append(digit);
        }
    }
}
