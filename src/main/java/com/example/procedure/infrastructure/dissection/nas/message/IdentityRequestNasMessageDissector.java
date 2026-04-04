package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class IdentityRequestNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x5b;
    }

    @Override
    public String messageTypeName() {
        return "Identity request";
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
        int identityTypeOctet = reader.u8(bodyOffset);
        String value = Integer.toString(identityTypeOctet & 0x07);
        flatFields.put("nas-5gs.mm.type_id", value);
        messageNode.addChild(new DecodedFieldNode("nas-5gs.mm.type_id", value, bodyOffset, 1));
    }
}
