package com.example.procedure.infrastructure.decode.nativews;

/**
 * Thin abstraction over the eventual JNI/JNA bridge.
 */
public interface NativeWiresharkBridgeClient {

    /**
     * Decode a NAS-5GS payload and return the raw JSON envelope emitted by the
     * native bridge.
     */
    String decodeNas5gs(NativeWiresharkNasRequest request);
}
