package com.example.procedure.decodebridge;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.wireshark.TsharkRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class PcapParseBridgeServiceImpl implements PcapParseBridgeService {

    private final TsharkRunner tsharkRunner;

    public PcapParseBridgeServiceImpl(TsharkRunner tsharkRunner) {
        this.tsharkRunner = tsharkRunner;
    }

    @Override
    public void parsePcap(Path pcap,
                          Set<String> wanted,
                          Set<String> enabledRaw,
                          Consumer<SignalingMessage> messageConsumer) throws Exception {

        Objects.requireNonNull(pcap, "pcap must not be null");
        Objects.requireNonNull(wanted, "wanted must not be null");
        Objects.requireNonNull(enabledRaw, "enabledRaw must not be null");
        Objects.requireNonNull(messageConsumer, "messageConsumer must not be null");

        ChainsInspectConsumer consumer = new ChainsInspectConsumer(messageConsumer);

        tsharkRunner.decodeToJsonStream(pcap, in -> {
            try {
                LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse tshark json stream for pcap: " + pcap, e);
            }
        });
    }
}