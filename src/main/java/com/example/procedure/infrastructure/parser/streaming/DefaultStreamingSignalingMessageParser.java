package com.example.procedure.infrastructure.parser.streaming;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.infrastructure.parser.streaming.layers.LayersSelectiveParser;
import com.example.procedure.infrastructure.parser.streaming.layers.StreamingMessageEmitter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default streaming parser implementation backed by the layer-selective tshark
 * JSON scanner.
 */
@Service
public class DefaultStreamingSignalingMessageParser implements StreamingSignalingMessageParser {

    @Override
    public void parse(
            InputStream in,
            Set<String> wantedFields,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> onMessage
    ) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(wantedFields, "wantedFields must not be null");
        Objects.requireNonNull(enabledRawLayers, "enabledRawLayers must not be null");
        Objects.requireNonNull(onMessage, "onMessage must not be null");

        LayersSelectiveParser.parsePackets(
                in,
                wantedFields,
                enabledRawLayers,
                new StreamingMessageEmitter(onMessage)
        );
    }
}
