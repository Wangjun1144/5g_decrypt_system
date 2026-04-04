package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SecurityModeCommandNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x5d;
    }

    @Override
    public String messageTypeName() {
        return "Security mode command";
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
        int selected = reader.u8(bodyOffset);
        String enc = Integer.toString((selected >>> 4) & 0x0f);
        String ip = Integer.toString(selected & 0x0f);
        flatFields.put("nas-5gs.mm.nas_sec_algo_enc", enc);
        flatFields.put("nas-5gs.mm.nas_sec_algo_ip", ip);
        messageNode.addChild(new DecodedFieldNode("nas-5gs.mm.nas_sec_algo_enc", enc, bodyOffset, 1));
        messageNode.addChild(new DecodedFieldNode("nas-5gs.mm.nas_sec_algo_ip", ip, bodyOffset, 1));

        if (reader.remaining(bodyOffset) < 2) {
            return;
        }
        int ngKsiOctet = reader.u8(bodyOffset + 1);
        String tsc = Integer.toString((ngKsiOctet >>> 3) & 0x01);
        String ksi = Integer.toString(ngKsiOctet & 0x07);
        flatFields.put("nas-5gs.mm.tsc", tsc);
        flatFields.put("nas-5gs.mm.nas_key_set_id", ksi);
        messageNode.addChild(new DecodedFieldNode("nas-5gs.mm.tsc", tsc, bodyOffset + 1, 1));
        messageNode.addChild(new DecodedFieldNode("nas-5gs.mm.nas_key_set_id", ksi, bodyOffset + 1, 1));

        int ueSecurityCapabilityOffset = bodyOffset + 2;
        if (reader.remaining(ueSecurityCapabilityOffset) < 1) {
            return;
        }
        int capabilityLength = reader.u8(ueSecurityCapabilityOffset);
        flatFields.put("gsm_a.len", Integer.toString(capabilityLength));
        messageNode.addChild(new DecodedFieldNode("gsm_a.len", Integer.toString(capabilityLength), ueSecurityCapabilityOffset, 1));
        if (capabilityLength < 2 || reader.remaining(ueSecurityCapabilityOffset + 1) < capabilityLength) {
            return;
        }

        int eaOctet = reader.u8(ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ea0", (eaOctet >>> 7) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.128_5g_ea1", (eaOctet >>> 6) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.128_5g_ea2", (eaOctet >>> 5) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.128_5g_ea3", (eaOctet >>> 4) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ea4", (eaOctet >>> 3) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ea5", (eaOctet >>> 2) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ea6", (eaOctet >>> 1) & 0x01, ueSecurityCapabilityOffset + 1);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ea7", eaOctet & 0x01, ueSecurityCapabilityOffset + 1);

        int iaOctet = reader.u8(ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.ia0", (iaOctet >>> 7) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_128_ia1", (iaOctet >>> 6) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_128_ia2", (iaOctet >>> 5) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_128_ia3", (iaOctet >>> 4) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_128_ia4", (iaOctet >>> 3) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ia5", (iaOctet >>> 2) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ia6", (iaOctet >>> 1) & 0x01, ueSecurityCapabilityOffset + 2);
        addBitField(messageNode, flatFields, "nas-5gs.mm.5g_ia7", iaOctet & 0x01, ueSecurityCapabilityOffset + 2);
    }

    private void addBitField(
            DecodedFieldNode messageNode,
            Map<String, String> flatFields,
            String name,
            int value,
            int offset
    ) {
        String text = Integer.toString(value);
        flatFields.put(name, text);
        messageNode.addChild(new DecodedFieldNode(name, text, offset, 1));
    }
}
