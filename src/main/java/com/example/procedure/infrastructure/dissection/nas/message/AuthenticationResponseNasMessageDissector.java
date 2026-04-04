package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsCommonIeDecoder;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class AuthenticationResponseNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x57;
    }

    @Override
    public String messageTypeName() {
        return "Authentication response";
    }

    @Override
    public void dissect(
            PacketBuffer fullMessage,
            Nas5gsIeReader reader,
            int bodyOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        if (reader.remaining(bodyOffset) < 2) {
            return;
        }
        int elementId = reader.u8(bodyOffset);
        int valueLength = reader.u8(bodyOffset + 1);
        flatFields.put("nas-eps.emm.elem_id", Integer.toString(elementId));
        flatFields.put("gsm_a.len", Integer.toString(valueLength));
        messageNode.addChild(new DecodedFieldNode("nas-eps.emm.elem_id", Integer.toString(elementId), bodyOffset, 1));
        messageNode.addChild(new DecodedFieldNode("gsm_a.len", Integer.toString(valueLength), bodyOffset + 1, 1));
        if (reader.remaining(bodyOffset + 2) < valueLength) {
            return;
        }
        String res = hex(reader.slice(bodyOffset + 2, valueLength).toByteArray());
        flatFields.put("nas-eps.emm.res", res);
        messageNode.addChild(new DecodedFieldNode("nas-eps.emm.res", res, bodyOffset + 2, valueLength));

        Nas5gsCommonIeDecoder.decodeOptionalEapMessageTlvE(
                reader,
                bodyOffset + 2 + valueLength,
                messageNode
        );
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
