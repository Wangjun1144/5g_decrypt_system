package com.example.procedure.infrastructure.decode.nativews;

import java.util.Arrays;

/**
 * Request for direct MAC-NR chain decoding.
 */
public final class NativeWiresharkMacNrRequest {

    private final byte[] payload;
    private final boolean includeFieldTree;
    private final boolean includeOffsets;

    public NativeWiresharkMacNrRequest(byte[] payload, boolean includeFieldTree, boolean includeOffsets) {
        this.payload = Arrays.copyOf(payload, payload.length);
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
