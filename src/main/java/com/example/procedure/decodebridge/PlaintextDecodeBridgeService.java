package com.example.procedure.decodebridge;

import java.io.InputStream;
import java.util.function.Consumer;

public interface PlaintextDecodeBridgeService {

    DebugDecodeArtifacts buildDebugArtifacts(PlaintextDecodeRequest request) throws Exception;

    void streamDecodedJson(PlaintextDecodeRequest request,
                           Consumer<InputStream> jsonConsumer) throws Exception;
}