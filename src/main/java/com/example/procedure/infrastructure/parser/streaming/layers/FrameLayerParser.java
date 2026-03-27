package com.example.procedure.infrastructure.parser.streaming.layers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Parses frame-layer metadata from tshark packet JSON.
 */
public class FrameLayerParser {

    /**
     * Parses the frame object into immutable frame metadata.
     */
    public FrameLayerMetadata parse(JsonParser parser, JsonToken token) throws IOException {
        if (token != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return null;
        }

        String number = null;
        String timeEpoch = null;
        String protocols = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                parser.skipChildren();
                continue;
            }

            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();

            if ("frame.number".equals(field) && valueToken.isScalarValue()) {
                number = parser.getValueAsString();
            } else if ("frame.time_epoch".equals(field) && valueToken.isScalarValue()) {
                timeEpoch = parser.getValueAsString();
            } else if ("frame.protocols".equals(field) && valueToken.isScalarValue()) {
                protocols = parser.getValueAsString();
            } else {
                parser.skipChildren();
            }
        }

        return new FrameLayerMetadata(
                safeParseLong(number, 0L),
                safeParseEpochMs(timeEpoch, 0L),
                protocols,
                splitProtocols(protocols)
        );
    }

    private long safeParseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long safeParseEpochMs(String value, long fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            double seconds = Double.parseDouble(value);
            return (long) (seconds * 1000L);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> splitProtocols(String protocolString) {
        if (protocolString == null || protocolString.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(protocolString.split(":"));
    }
}
