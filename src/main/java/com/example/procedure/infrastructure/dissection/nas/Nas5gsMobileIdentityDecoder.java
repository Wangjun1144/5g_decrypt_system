package com.example.procedure.infrastructure.dissection.nas;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;

import java.util.Map;

/**
 * Shared 5GS mobile identity decoder helpers used by structured NAS message
 * dissectors. This keeps the field-level behavior aligned across messages while
 * staying isolated from the legacy tshark-driven path.
 */
public final class Nas5gsMobileIdentityDecoder {

    private Nas5gsMobileIdentityDecoder() {
    }

    public static void decodeRegistrationRequestIdentity(
            Nas5gsIeReader reader,
            int mobileIdentityOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(mobileIdentityOffset) < 3) {
            return;
        }

        int mobileIdentityLength = reader.u16(mobileIdentityOffset);
        putField(messageNode, flatFields, "gsm_a.len", Integer.toString(mobileIdentityLength), mobileIdentityOffset, 2, true);
        if (reader.remaining(mobileIdentityOffset + 2) < mobileIdentityLength) {
            return;
        }

        DecodedFieldNode identityNode = new DecodedFieldNode(
                "5GS mobile identity",
                "",
                mobileIdentityOffset + 2,
                mobileIdentityLength
        );
        messageNode.addChild(identityNode);

        int identityHeader = reader.u8(mobileIdentityOffset + 2);
        putField(identityNode, flatFields, "nas-5gs.spare_b7", Integer.toString((identityHeader >>> 7) & 0x01), mobileIdentityOffset + 2, 1, true);
        putField(identityNode, flatFields, "nas-5gs.spare_b6", Integer.toString((identityHeader >>> 6) & 0x01), mobileIdentityOffset + 2, 1, true);
        putField(identityNode, flatFields, "nas-5gs.spare_b5", Integer.toString((identityHeader >>> 5) & 0x01), mobileIdentityOffset + 2, 1, true);
        putField(identityNode, flatFields, "nas-5gs.spare_b4", Integer.toString((identityHeader >>> 4) & 0x01), mobileIdentityOffset + 2, 1, true);
        putField(identityNode, flatFields, "nas-5gs.spare_b3", Integer.toString((identityHeader >>> 3) & 0x01), mobileIdentityOffset + 2, 1, true);
        int typeId = identityHeader & 0x07;
        putField(identityNode, flatFields, "nas-5gs.mm.type_id", Integer.toString(typeId), mobileIdentityOffset + 2, 1, true);

        if (typeId == 3 || typeId == 5) {
            putField(identityNode, flatFields, "nas-5gs.mm.odd_even", Integer.toString((identityHeader >>> 3) & 0x01), mobileIdentityOffset + 2, 1, true);
            decodeBcdIdentity(
                    reader,
                    mobileIdentityOffset + 2,
                    mobileIdentityLength,
                    flatFields,
                    identityNode,
                    typeId == 3 ? "nas-5gs.mm.imei" : "nas-5gs.mm.imeisv"
            );
            return;
        }

        if (typeId == 4) {
            putField(identityNode, flatFields, "nas-5gs.mm.odd_even", Integer.toString((identityHeader >>> 3) & 0x01), mobileIdentityOffset + 2, 1, true);
            decodeFiveGsStmsi(reader, mobileIdentityOffset + 3, flatFields, identityNode);
            return;
        }

        if (typeId == 6) {
            putField(identityNode, flatFields, "nas-5gs.mm.mauri", Integer.toString((identityHeader >>> 3) & 0x01), mobileIdentityOffset + 2, 1, true);
            if (reader.remaining(mobileIdentityOffset + 3) >= 6) {
                putField(
                        identityNode,
                        flatFields,
                        "nas-5gs.mm.mac_addr",
                        colonHex(reader.slice(mobileIdentityOffset + 3, 6).toByteArray()),
                        mobileIdentityOffset + 3,
                        6,
                        true
                );
            }
            return;
        }

        if (typeId == 7) {
            if (reader.remaining(mobileIdentityOffset + 3) >= 8) {
                putField(
                        identityNode,
                        flatFields,
                        "nas-5gs.mm.eui_64",
                        colonHex(reader.slice(mobileIdentityOffset + 3, 8).toByteArray()),
                        mobileIdentityOffset + 3,
                        8,
                        true
                );
            }
            return;
        }

        if (mobileIdentityLength < 11 || reader.remaining(mobileIdentityOffset + 3) < 10) {
            return;
        }

        int guamiOffset = mobileIdentityOffset + 3;
        int b1 = reader.u8(guamiOffset);
        int b2 = reader.u8(guamiOffset + 1);
        int b3 = reader.u8(guamiOffset + 2);
        putField(identityNode, flatFields, "e212.guami.mcc", decodeMcc(b1, b2), guamiOffset, 2, true);
        putField(identityNode, flatFields, "e212.guami.mnc", decodeMnc(b2, b3), guamiOffset + 1, 2, true);
        putField(identityNode, flatFields, "nas-5gs.amf_region_id", Integer.toString(reader.u8(guamiOffset + 3)), guamiOffset + 3, 1, true);

        int amfSetPointer = reader.u16(guamiOffset + 4);
        putField(identityNode, flatFields, "nas-5gs.amf_set_id", Integer.toString((amfSetPointer >>> 6) & 0x03ff), guamiOffset + 4, 2, true);
        putField(identityNode, flatFields, "nas-5gs.amf_pointer", Integer.toString(amfSetPointer & 0x003f), guamiOffset + 5, 1, true);

        if (reader.remaining(guamiOffset + 6) >= 4) {
            String tmsiHex = hex(reader.slice(guamiOffset + 6, 4).toByteArray());
            putField(identityNode, flatFields, "nas-5gs.5g_tmsi", tmsiHex, guamiOffset + 6, 4, true);
            putField(
                    identityNode,
                    flatFields,
                    "3gpp.tmsi",
                    Long.toString(Long.parseUnsignedLong(tmsiHex, 16)),
                    guamiOffset + 6,
                    4,
                    true
            );
        }
    }

    private static void decodeFiveGsStmsi(
            Nas5gsIeReader reader,
            int offset,
            Map<String, String> flatFields,
            DecodedFieldNode identityNode
    ) {
        if (reader.remaining(offset) < 6) {
            return;
        }
        int amfSetPointer = reader.u16(offset);
        putField(identityNode, flatFields, "nas-5gs.amf_set_id", Integer.toString((amfSetPointer >>> 6) & 0x03ff), offset, 2, true);
        putField(identityNode, flatFields, "nas-5gs.amf_pointer", Integer.toString(reader.u8(offset + 1) & 0x003f), offset + 1, 1, true);
        String tmsiHex = hex(reader.slice(offset + 2, 4).toByteArray());
        putField(identityNode, flatFields, "nas-5gs.5g_tmsi", tmsiHex, offset + 2, 4, true);
        putField(
                identityNode,
                flatFields,
                "3gpp.tmsi",
                Long.toString(Long.parseUnsignedLong(tmsiHex, 16)),
                offset + 2,
                4,
                true
        );
    }

    private static void decodeBcdIdentity(
            Nas5gsIeReader reader,
            int offset,
            int length,
            Map<String, String> flatFields,
            DecodedFieldNode identityNode,
            String fieldName
    ) {
        putField(
                identityNode,
                flatFields,
                fieldName,
                decodeBcdDigitsSkippingFirstNibble(reader.slice(offset, length).toByteArray()),
                offset,
                length,
                true
        );
    }

    public static void decodeSuciIdentity(
            Nas5gsIeReader reader,
            int mobileIdentityOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(mobileIdentityOffset) < 3) {
            return;
        }

        int mobileIdentityLength = reader.u16(mobileIdentityOffset);
        putField(messageNode, flatFields, "gsm_a.len", Integer.toString(mobileIdentityLength), mobileIdentityOffset, 2, false);
        if (reader.remaining(mobileIdentityOffset + 2) < mobileIdentityLength) {
            return;
        }

        DecodedFieldNode identityNode = new DecodedFieldNode(
                "5GS mobile identity",
                "",
                mobileIdentityOffset + 2,
                mobileIdentityLength
        );
        messageNode.addChild(identityNode);

        int identityHeader = reader.u8(mobileIdentityOffset + 2);
        putField(identityNode, flatFields, "nas-5gs.spare_b7", Integer.toString((identityHeader >>> 7) & 0x01), mobileIdentityOffset + 2, 1, false);
        putField(identityNode, flatFields, "nas-5gs.mm.suci.supi_fmt", Integer.toString((identityHeader >>> 4) & 0x07), mobileIdentityOffset + 2, 1, false);
        putField(identityNode, flatFields, "nas-5gs.spare_b3", Integer.toString((identityHeader >>> 3) & 0x01), mobileIdentityOffset + 2, 1, false);
        int typeId = identityHeader & 0x07;
        putField(identityNode, flatFields, "nas-5gs.mm.type_id", Integer.toString(typeId), mobileIdentityOffset + 2, 1, false);

        int supiFormat = (identityHeader >>> 4) & 0x07;
        if (supiFormat >= 1 && supiFormat <= 3) {
            int naiLength = mobileIdentityLength - 1;
            if (naiLength > 0 && reader.remaining(mobileIdentityOffset + 3) >= naiLength) {
                putField(
                        identityNode,
                        flatFields,
                        "nas-5gs.mm.suci.nai",
                        new String(reader.slice(mobileIdentityOffset + 3, naiLength).toByteArray(), java.nio.charset.StandardCharsets.UTF_8),
                        mobileIdentityOffset + 3,
                        naiLength,
                        false
                );
            }
            return;
        }

        if (mobileIdentityLength < 13 || reader.remaining(mobileIdentityOffset + 3) < 10) {
            return;
        }

        int plmnOffset = mobileIdentityOffset + 3;
        int b1 = reader.u8(plmnOffset);
        int b2 = reader.u8(plmnOffset + 1);
        int b3 = reader.u8(plmnOffset + 2);
        putField(identityNode, flatFields, "e212.mcc", decodeMcc(b1, b2), plmnOffset, 2, false);
        putField(identityNode, flatFields, "e212.mnc", decodeMnc(b2, b3), plmnOffset + 1, 2, false);

        putField(
                identityNode,
                flatFields,
                "nas-5gs.mm.suci.routing_indicator",
                decodeTbcd(reader.slice(plmnOffset + 3, 2).toByteArray()),
                plmnOffset + 3,
                2,
                false
        );

        int schemeAndPkiOffset = plmnOffset + 5;
        putField(
                identityNode,
                flatFields,
                "nas-5gs.mm.suci.scheme_id",
                Integer.toString(reader.u8(schemeAndPkiOffset) & 0x0f),
                schemeAndPkiOffset,
                1,
                false
        );
        putField(
                identityNode,
                flatFields,
                "nas-5gs.mm.suci.pki",
                Integer.toString(reader.u8(schemeAndPkiOffset + 1)),
                schemeAndPkiOffset + 1,
                1,
                false
        );

        int msinLength = mobileIdentityLength - 8;
        if (msinLength > 0 && reader.remaining(schemeAndPkiOffset + 2) >= msinLength) {
            int schemeId = reader.u8(schemeAndPkiOffset) & 0x0f;
            if (schemeId == 0) {
                putField(
                        identityNode,
                        flatFields,
                        "nas-5gs.mm.suci.msin",
                        decodeTbcd(reader.slice(schemeAndPkiOffset + 2, msinLength).toByteArray()),
                        schemeAndPkiOffset + 2,
                        msinLength,
                        false
                );
            } else {
                putField(
                        identityNode,
                        flatFields,
                        "nas-5gs.mm.suci.scheme_output",
                        hex(reader.slice(schemeAndPkiOffset + 2, msinLength).toByteArray()),
                        schemeAndPkiOffset + 2,
                        msinLength,
                        false
                );
                int publicKeyLength = schemeId == 1 ? 32 : (schemeId == 2 ? 33 : -1);
                if (publicKeyLength > 0 && msinLength >= publicKeyLength + 8) {
                    putField(
                            identityNode,
                            flatFields,
                            "nas-5gs.mm.suci.scheme_output.ecc_public_key",
                            hex(reader.slice(schemeAndPkiOffset + 2, publicKeyLength).toByteArray()),
                            schemeAndPkiOffset + 2,
                            publicKeyLength,
                            false
                    );
                    int ciphertextLength = msinLength - publicKeyLength - 8;
                    if (ciphertextLength > 0) {
                        putField(
                                identityNode,
                                flatFields,
                                "nas-5gs.mm.suci.scheme_output.ciphertext",
                                hex(reader.slice(schemeAndPkiOffset + 2 + publicKeyLength, ciphertextLength).toByteArray()),
                                schemeAndPkiOffset + 2 + publicKeyLength,
                                ciphertextLength,
                                false
                        );
                    }
                    putField(
                            identityNode,
                            flatFields,
                            "nas-5gs.mm.suci.scheme_output.mac_tag",
                            hex(reader.slice(schemeAndPkiOffset + 2 + publicKeyLength + Math.max(ciphertextLength, 0), 8).toByteArray()),
                            schemeAndPkiOffset + 2 + publicKeyLength + Math.max(ciphertextLength, 0),
                            8,
                            false
                    );
                }
            }
        }
    }

    private static void putField(
            DecodedFieldNode parent,
            Map<String, String> flatFields,
            String name,
            String value,
            int offset,
            int length,
            boolean preserveFirst
    ) {
        if (preserveFirst) {
            flatFields.putIfAbsent(name, value);
        } else {
            flatFields.put(name, value);
        }
        parent.addChild(new DecodedFieldNode(name, value, offset, length));
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

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String colonHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    private static String decodeBcdDigitsSkippingFirstNibble(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int low = bytes[i] & 0x0f;
            int high = (bytes[i] >>> 4) & 0x0f;
            if (i > 0) {
                appendTbcdDigit(sb, low);
            }
            appendTbcdDigit(sb, high);
        }
        return sb.toString();
    }
}
