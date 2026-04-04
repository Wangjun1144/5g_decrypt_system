package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsCommonIeDecoder;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class AuthenticationRequestNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x56;
    }

    @Override
    public String messageTypeName() {
        return "Authentication request";
    }

    @Override
    public void dissect(
            PacketBuffer fullMessage,
            Nas5gsIeReader reader,
            int bodyOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(bodyOffset) < 1) {
            return;
        }
        int ngKsiOctet = reader.u8(bodyOffset);
        putField(messageNode, flatFields, "nas-5gs.mm.tsc", Integer.toString((ngKsiOctet >>> 3) & 0x01), bodyOffset, 1, false);
        putField(messageNode, flatFields, "nas-5gs.mm.nas_key_set_id", Integer.toString(ngKsiOctet & 0x07), bodyOffset, 1, false);

        int abbaOffset = bodyOffset + 1;
        if (reader.remaining(abbaOffset) < 1) {
            return;
        }
        int abbaLength = reader.u8(abbaOffset);
        DecodedFieldNode abbaNode = new DecodedFieldNode("ABBA", "", abbaOffset, Math.min(reader.remaining(abbaOffset), 1 + abbaLength));
        messageNode.addChild(abbaNode);
        putField(abbaNode, flatFields, "gsm_a.len", Integer.toString(abbaLength), abbaOffset, 1, true);
        if (reader.remaining(abbaOffset + 1) < abbaLength) {
            return;
        }
        putField(
                abbaNode,
                flatFields,
                "nas-5gs.mm.abba_contents",
                colonHex(reader.slice(abbaOffset + 1, abbaLength).toByteArray()),
                abbaOffset + 1,
                abbaLength,
                false
        );

        int randOffset = abbaOffset + 1 + abbaLength;
        if (reader.remaining(randOffset) < 17) {
            return;
        }
        DecodedFieldNode randNode = new DecodedFieldNode(
                "Authentication Parameter RAND - 5G authentication challenge",
                "",
                randOffset,
                17
        );
        messageNode.addChild(randNode);
        putField(randNode, flatFields, "gsm_a.dtap.elem_id", String.format(Locale.ROOT, "0x%02x", reader.u8(randOffset)), randOffset, 1, true);
        putField(
                randNode,
                flatFields,
                "gsm_a.dtap.rand",
                colonHex(reader.slice(randOffset + 1, 16).toByteArray()),
                randOffset + 1,
                16,
                false
        );

        int autnOffset = randOffset + 17;
        if (reader.remaining(autnOffset) < 2) {
            return;
        }
        int autnLength = reader.u8(autnOffset + 1);
        if (reader.remaining(autnOffset + 2) < autnLength) {
            return;
        }
        DecodedFieldNode autnNode = new DecodedFieldNode(
                "Authentication Parameter AUTN (UMTS and EPS authentication challenge) - 5G authentication challenge",
                "",
                autnOffset,
                2 + autnLength
        );
        messageNode.addChild(autnNode);
        putField(autnNode, flatFields, "gsm_a.len", Integer.toString(autnLength), autnOffset + 1, 1, true);
        putField(
                autnNode,
                flatFields,
                "gsm_a.dtap.autn",
                colonHex(reader.slice(autnOffset + 2, autnLength).toByteArray()),
                autnOffset + 2,
                autnLength,
                false
        );
        if (autnLength >= 16) {
            DecodedFieldNode autnTree = new DecodedFieldNode("gsm_a.dtap.autn_tree", "", autnOffset + 2, autnLength);
            autnNode.addChild(autnTree);
            putField(
                    autnTree,
                    flatFields,
                    "gsm_a.dtap.autn.sqn_xor_ak",
                    colonHex(reader.slice(autnOffset + 2, 6).toByteArray()),
                    autnOffset + 2,
                    6,
                    false
            );
            putField(
                    autnTree,
                    flatFields,
                    "gsm_a.dtap.autn.amf",
                    colonHex(reader.slice(autnOffset + 8, 2).toByteArray()),
                    autnOffset + 8,
                    2,
                    false
            );
            putField(
                    autnTree,
                    flatFields,
                    "gsm_a.dtap.autn.mac",
                    colonHex(reader.slice(autnOffset + 10, 8).toByteArray()),
                    autnOffset + 10,
                    8,
                    false
            );
        }

        Nas5gsCommonIeDecoder.decodeOptionalEapMessageTlvE(
                reader,
                autnOffset + 2 + autnLength,
                messageNode
        );
    }

    private void putField(
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

    private String colonHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }
}
