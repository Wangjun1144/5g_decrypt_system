package com.example.procedure.infrastructure.dissection.nas.message;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsIeReader;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsMobileIdentityDecoder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Structured Identity Response message dissector aligned with the current
 * Wireshark-visible field layout for SUCI-based identity content.
 */
@Component
public class IdentityResponseNasMessageDissector implements Nas5gsMessageDissector {

    @Override
    public boolean supports(int messageType) {
        return messageType == 0x5c;
    }

    @Override
    public String messageTypeName() {
        return "Identity response";
    }

    @Override
    public void dissect(
            PacketBuffer fullMessage,
            Nas5gsIeReader reader,
            int bodyOffset,
            Map<String, String> flatFields,
            DecodedFieldNode messageNode
    ) {
        Nas5gsMobileIdentityDecoder.decodeSuciIdentity(reader, bodyOffset, flatFields, messageNode);
    }
}
