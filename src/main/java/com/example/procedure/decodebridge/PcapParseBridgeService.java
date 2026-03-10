package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

public interface PcapParseBridgeService {

    void parsePcap(Path pcap,
                   Set<String> wanted,
                   Set<String> enabledRaw,
                   Consumer<SignalingMessage> messageConsumer) throws Exception;
}