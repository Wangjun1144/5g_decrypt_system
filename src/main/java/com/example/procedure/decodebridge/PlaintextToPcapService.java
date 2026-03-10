package com.example.procedure.decodebridge;

public interface PlaintextToPcapService {

    DebugPcapBuildResult buildDebugPcap(PlaintextDecodeRequest request) throws Exception;

    StreamingPcapHandle buildStreamingPcap(PlaintextDecodeRequest request) throws Exception;
}