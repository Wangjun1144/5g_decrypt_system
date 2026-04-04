package com.example.procedure.infrastructure.decode.nativews;

import java.util.Arrays;
import java.util.Objects;

/**
 * Phase-1 request for direct NAS-5GS byte decoding through a native bridge.
 */
public final class NativeWiresharkNasRequest {

    private final byte[] payload;
    private final boolean includeFieldTree;
    private final boolean includeOffsets;

    public NativeWiresharkNasRequest(byte[] payload, boolean includeFieldTree, boolean includeOffsets) {
        this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload must not be null"), payload.length);
        this.includeFieldTree = includeFieldTree;
        this.includeOffsets = includeOffsets;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public boolean isIncludeFieldTree() {
        return includeFieldTree;
    }

    public boolean isIncludeOffsets() {
        return includeOffsets;
    }
}
