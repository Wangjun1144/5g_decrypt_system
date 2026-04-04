package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RegistrationCompleteNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x45;
    }

    @Override
    public String messageTypeName() {
        return "Registration complete";
    }

    @Override
    public void dissect(
            PacketBuffer fullMessage,
            Nas5gsIeReader reader,
            int bodyOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        // Currently no stable body fields required beyond message identity.
    }
}
