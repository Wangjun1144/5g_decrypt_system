package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugDecodeArtifacts;
import com.example.procedure.model.SignalingMessage;

import java.util.Set;
import java.util.function.Consumer;

public interface PlaintextPacketParseBridgeService {

    DebugDecodeArtifacts debugBuildAndParse(PlaintextDecodeRequest request,
                                            Set<String> wanted,
                                            Set<String> enabledRaw,
                                            Consumer<SignalingMessage> messageConsumer) throws Exception;

    void streamBuildAndParse(PlaintextDecodeRequest request,
                             Set<String> wanted,
                             Set<String> enabledRaw,
                             Consumer<SignalingMessage> messageConsumer) throws Exception;
}
