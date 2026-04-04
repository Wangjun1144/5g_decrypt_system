package com.example.procedure.infrastructure.dissection.nas;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.message.Nas5gsMessageDissector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated structured NAS dissector path that mirrors Wireshark's common
 * pattern: parse common header first, then hand off to a message-specific
 * dissector.
 */
@Component
public class Nas5gsStructuredDissector {

    private final List<Nas5gsMessageDissector> messageDissectors;

    public Nas5gsStructuredDissector(List<Nas5gsMessageDissector> messageDissectors) {
        this.messageDissectors = messageDissectors == null ? List.of() : List.copyOf(messageDissectors);
    }

    public Nas5gsStructuredDecodeResult dissect(PacketBuffer buffer) {
        if (buffer == null || buffer.length() < 2) {
            return new Nas5gsStructuredDecodeResult(-1, null, Map.of(), List.of());
        }

        Map<String, String> flatFields = new LinkedHashMap<>();
        List<DecodedFieldNode> tree = new ArrayList<>();
        DecodedFieldNode root = new DecodedFieldNode("nas-5gs", "", 0, buffer.length());
        tree.add(root);

        int epd = buffer.getU8(0);
        int secondOctet = buffer.getU8(1);
        int spareHalfOctet = (secondOctet >>> 4) & 0x0f;
        int securityHeaderType = secondOctet & 0x0f;
        putField(root, flatFields, "nas-5gs.epd", Integer.toString(epd), 0, 1);
        putField(root, flatFields, "nas-5gs.spare_half_octet", Integer.toString(spareHalfOctet), 1, 1);
        putField(root, flatFields, "nas-5gs.security_header_type", Integer.toString(securityHeaderType), 1, 1);

        if (securityHeaderType == 0) {
            if (buffer.length() < 3) {
                return new Nas5gsStructuredDecodeResult(-1, null, flatFields, tree);
            }
            int messageType = buffer.getU8(2);
            putField(root, flatFields, "nas-5gs.mm.message_type", Integer.toString(messageType), 2, 1);
            String messageTypeName = dissectMessage(buffer, root, flatFields, messageType, 3, "");
            return new Nas5gsStructuredDecodeResult(messageType, messageTypeName, flatFields, tree);
        }

        if (buffer.length() >= 6) {
            putField(root, flatFields, "nas-5gs.msg_auth_code", hex(buffer.slice(2, 4).toByteArray()), 2, 4);
        }
        if (buffer.length() >= 7) {
            putField(root, flatFields, "nas-5gs.seq_no", Integer.toString(buffer.getU8(6)), 6, 1);
        }

        if (buffer.length() >= 10 && buffer.getU8(7) == 0x7e) {
            putField(root, flatFields, "nas-5gs.inner.epd", Integer.toString(buffer.getU8(7)), 7, 1);
            int innerSecondOctet = buffer.getU8(8);
            putField(root, flatFields, "nas-5gs.inner.spare_half_octet", Integer.toString((innerSecondOctet >>> 4) & 0x0f), 8, 1);
            putField(root, flatFields, "nas-5gs.inner.security_header_type", Integer.toString(innerSecondOctet & 0x0f), 8, 1);
            int messageType = buffer.getU8(9);
            putField(root, flatFields, "nas-5gs.mm.message_type", Integer.toString(messageType), 9, 1);
            String messageTypeName = dissectMessage(buffer, root, flatFields, messageType, 10, "");
            return new Nas5gsStructuredDecodeResult(messageType, messageTypeName, flatFields, tree);
        }

        return new Nas5gsStructuredDecodeResult(-1, null, flatFields, tree);
    }

    private String dissectMessage(
            PacketBuffer buffer,
            DecodedFieldNode root,
            Map<String, String> flatFields,
            int messageType,
            int bodyOffset,
            String fieldPrefix
    ) {
        Nas5gsMessageDissector messageDissector = findMessageDissector(messageType);
        String messageTypeName = messageDissector == null ? null : messageDissector.messageTypeName();
        if (messageTypeName != null) {
            flatFields.put("nas-5gs.mm.message_type_name", messageTypeName);
        }
        DecodedFieldNode messageNode = new DecodedFieldNode(
                messageTypeName == null ? "nas-5gs.message_body" : messageTypeName,
                "",
                bodyOffset,
                Math.max(0, buffer.length() - bodyOffset)
        );
        root.addChild(messageNode);
        if (messageDissector != null) {
            messageDissector.dissect(
                    buffer,
                    new Nas5gsIeReader(buffer),
                    bodyOffset,
                    flatFields,
                    messageNode
            );
        }
        return messageTypeName;
    }

    private Nas5gsMessageDissector findMessageDissector(int messageType) {
        for (Nas5gsMessageDissector dissector : messageDissectors) {
            if (dissector.supports(messageType)) {
                return dissector;
            }
        }
        return null;
    }

    private void putField(
            DecodedFieldNode root,
            Map<String, String> flatFields,
            String name,
            String value,
            int offset,
            int length
    ) {
        flatFields.put(name, value);
        root.addChild(new DecodedFieldNode(name, value, offset, length));
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
