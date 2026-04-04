package com.example.procedure.infrastructure.dissection.entry;

import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.PacketContext;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDissector;
import org.springframework.stereotype.Component;

@Component
public class Nas5gsEntryDissector extends AbstractEntryDissector {

    private final Nas5gsStructuredDissector structuredDissector;

    public Nas5gsEntryDissector(Nas5gsStructuredDissector structuredDissector) {
        super("5G NAS", "NAS-5GS", "nas-5gs");
        this.structuredDissector = structuredDissector;
    }

    public Nas5gsEntryDissector() {
        this(new Nas5gsStructuredDissector(java.util.List.of(
                new com.example.procedure.infrastructure.dissection.nas.message.RegistrationRequestNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.RegistrationCompleteNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.IdentityRequestNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.IdentityResponseNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.AuthenticationRequestNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.AuthenticationResponseNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.SecurityModeCommandNasMessageDissector(),
                new com.example.procedure.infrastructure.dissection.nas.message.SecurityModeCompleteNasMessageDissector()
        )));
    }

    @Override
    public DissectionResult dissect(PacketBuffer buffer, PacketContext context) {
        context.addProtocol(protocolName());
        java.util.Map<String, String> decodedFields;
        java.util.List<com.example.procedure.infrastructure.dissection.field.DecodedFieldNode> fieldTree;
        try {
            com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDecodeResult structuredResult =
                    structuredDissector.dissect(buffer);
            decodedFields = structuredResult.getDecodedFields();
            fieldTree = structuredResult.getFieldTree();
            if (decodedFields == null || decodedFields.isEmpty()) {
                decodedFields = Nas5gsHeaderParser.parse(buffer);
                fieldTree = java.util.List.of();
            }
        } catch (Exception ignored) {
            decodedFields = Nas5gsHeaderParser.parse(buffer);
            fieldTree = java.util.List.of();
        }
        return DissectionResult.of(
                protocolName(),
                registration().shortName(),
                registration().displayName(),
                context.getProtocolTrace(),
                decodedFields,
                fieldTree
        );
    }
}
