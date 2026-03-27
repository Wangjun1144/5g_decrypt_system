package com.example.procedure.infrastructure.parser.streaming.layers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable frame-level metadata extracted before per-layer scanning begins.
 */
public class FrameLayerMetadata {

    private final long frameNo;
    private final long timestampMs;
    private final String protocols;
    private final List<String> protoList;

    /**
     * Creates one immutable frame metadata snapshot.
     */
    public FrameLayerMetadata(
            long frameNo,
            long timestampMs,
            String protocols,
            List<String> protoList
    ) {
        this.frameNo = frameNo;
        this.timestampMs = timestampMs;
        this.protocols = protocols;
        this.protoList = protoList == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(protoList));
    }

    public long getFrameNo() {
        return frameNo;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getProtocols() {
        return protocols;
    }

    public List<String> getProtoList() {
        return protoList;
    }
}
