package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.infrastructure.decode.TsharkDecodeJsonTool;
import com.example.procedure.infrastructure.parser.streaming.StreamingSignalingMessageParser;
import com.example.procedure.processing.pcap.PcapDecodeCommand;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

@Service
public class TsharkPcapDecodeGateway implements PcapDecodeGateway {

    // REFACTOR STEP: DECODEBRIDGE_PCAP_SUBPACKAGE
    private final TsharkDecodeJsonTool decoder;
    private final StreamingSignalingMessageParser streamingParser;

    public TsharkPcapDecodeGateway(
            TsharkDecodeJsonTool decoder,
            StreamingSignalingMessageParser streamingParser
    ) {
        this.decoder = decoder;
        this.streamingParser = streamingParser;
    }

    @Override
    public void decode(PcapDecodeCommand request) throws Exception {
        validateRequest(request);

        decoder.decodeToJsonStream(request.getPcap(), in -> {
            try {
                streamingParser.parse(
                        in,
                        request.getWantedLayers(),
                        request.getEnabledRawLayers(),
                        request.getMessageConsumer()
                );
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to parse tshark json stream for pcap: " + request.getPcap(),
                        e
                );
            }
        });
    }

    private void validateRequest(PcapDecodeCommand request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getPcap(), "pcap must not be null");
        Objects.requireNonNull(request.getWantedLayers(), "wantedLayers must not be null");
        Objects.requireNonNull(request.getEnabledRawLayers(), "enabledRawLayers must not be null");
        Objects.requireNonNull(request.getMessageConsumer(), "messageConsumer must not be null");
    }
}
