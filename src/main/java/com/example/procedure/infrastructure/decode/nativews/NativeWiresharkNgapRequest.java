package com.example.procedure.infrastructure.decode.nativews;

import java.util.Arrays;
import java.util.Objects;

/**
 * Request for direct NGAP byte decoding through the minimal ws-core DLL.
 */
public final class NativeWiresharkNgapRequest {

    private final byte[] payload;
    private final boolean includeFieldTree;
    private final boolean includeOffsets;

    public NativeWiresharkNgapRequest(byte[] payload, boolean includeFieldTree, boolean includeOffsets) {
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
