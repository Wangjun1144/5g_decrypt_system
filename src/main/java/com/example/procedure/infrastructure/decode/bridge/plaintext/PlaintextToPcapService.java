package com.example.procedure.infrastructure.decode.bridge.plaintext;

import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugPcapBuildResult;

public interface PlaintextToPcapService {

    DebugPcapBuildResult buildDebugPcap(PlaintextDecodeRequest request) throws Exception;

    StreamingPcapHandle buildStreamingPcap(PlaintextDecodeRequest request) throws Exception;
}
