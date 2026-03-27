package com.example.procedure.infrastructure.parser.streaming;

import com.example.procedure.model.SignalingMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Formal entry contract for the streaming signaling-message parser.
 *
 * This boundary represents the preferred parsing path for online or large-file
 * processing. It consumes tshark JSON as a stream and emits normalized
 * {@link SignalingMessage} objects incrementally.
 */
public interface StreamingSignalingMessageParser {

    /**
     * Parse tshark JSON incrementally and emit signaling messages as they are
     * assembled.
     *
     * @param in tshark JSON input stream
     * @param wantedFields logical layers to keep
     * @param enabledRawLayers raw layers that should participate in strict
     *                         sibling matching
     * @param onMessage consumer for parsed signaling messages
     * @throws IOException when the stream cannot be read or parsed
     */
    void parse(
            InputStream in,
            Set<String> wantedFields,
            Set<String> enabledRawLayers,
            Consumer<SignalingMessage> onMessage
    ) throws IOException;
}
