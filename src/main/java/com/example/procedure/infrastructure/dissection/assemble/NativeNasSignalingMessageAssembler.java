package com.example.procedure.infrastructure.dissection.assemble;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.NasInfo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds a minimal native {@link SignalingMessage} from the isolated NAS
 * dissection chain.
 */
@Component
public class NativeNasSignalingMessageAssembler {

    private final NativeNasInfoAssembler nasInfoAssembler;

    public NativeNasSignalingMessageAssembler(NativeNasInfoAssembler nasInfoAssembler) {
        this.nasInfoAssembler = nasInfoAssembler;
    }

    public SignalingMessage assemble(CapturedPacket packet, DissectionResult result) {
        NasInfo nasInfo = nasInfoAssembler.assemble(packet, result);

        SignalingMessage message = new SignalingMessage();
        message.setMsgId("NATIVE-FRAME-" + packet.getPacketIndex());
        message.setFrameNo(packet.getPacketIndex());
        message.setTimestamp(packet.getTimestamp().toEpochMilli());
        message.setIface(resolveInterface(packet.getLinkType(), result.getEntryProtocol()));
        message.setDirection("UNKNOWN");
        message.setProtocolLayer("NAS");
        message.setMsgType(result.getDecodedFields().getOrDefault("nas-5gs.mm.message_type_name", "NAS_5GS"));
        message.setNasList(List.of(nasInfo));
        message.setEncrypted(nasInfo.isEncrypted());
        message.setEncryptedType(nasInfo.isEncrypted() ? "NAS" : "NONE");
        return message;
    }

    private String resolveInterface(int linkType, String entryProtocol) {
        if ("nas-5gs".equals(entryProtocol) && linkType == 151) {
            return "Uu";
        }
        return "UNKNOWN";
    }
}
