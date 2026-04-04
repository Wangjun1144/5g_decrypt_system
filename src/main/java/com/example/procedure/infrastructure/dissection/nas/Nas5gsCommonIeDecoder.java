package com.example.procedure.infrastructure.dissection.nas;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;

import java.util.Locale;

/**
 * Source-aligned helpers for common NAS 5GS information elements that recur
 * across multiple MM messages.
 */
public final class Nas5gsCommonIeDecoder {

    private Nas5gsCommonIeDecoder() {
    }

    /**
     * Mirrors Wireshark's optional TLV-E EAP message handoff point. Wireshark
     * hands the payload to the EAP dissector; here we at least preserve the IE
     * boundary and payload bytes in the field tree so message-level parsing
     * stays source-ordered.
     *
     * @return next offset after the IE, or the original offset if the IE is not
     * present or incomplete.
     */
    public static int decodeOptionalEapMessageTlvE(
            Nas5gsIeReader reader,
            int offset,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(offset) < 3 || reader.u8(offset) != 0x78) {
            return offset;
        }
        int length = reader.u16(offset + 1);
        if (reader.remaining(offset + 3) < length) {
            return offset;
        }

        DecodedFieldNode eapNode = new DecodedFieldNode("EAP message", "", offset, 3 + length);
        messageNode.addChild(eapNode);
        eapNode.addChild(new DecodedFieldNode("Length", Integer.toString(length), offset + 1, 2));
        eapNode.addChild(new DecodedFieldNode(
                "Payload",
                hex(reader.slice(offset + 3, length).toByteArray()),
                offset + 3,
                length
        ));
        return offset + 3 + length;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
