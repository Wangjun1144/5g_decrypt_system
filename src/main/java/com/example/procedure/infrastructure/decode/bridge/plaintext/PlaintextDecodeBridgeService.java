package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugDecodeArtifacts;

import java.io.InputStream;
import java.util.function.Consumer;

public interface PlaintextDecodeBridgeService {

    DebugDecodeArtifacts buildDebugArtifacts(PlaintextDecodeRequest request) throws Exception;

    void streamDecodedJson(PlaintextDecodeRequest request,
                           Consumer<InputStream> jsonConsumer) throws Exception;
}
