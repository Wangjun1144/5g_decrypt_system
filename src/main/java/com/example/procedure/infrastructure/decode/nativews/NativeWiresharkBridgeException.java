package com.example.procedure.infrastructure.decode.nativews;

/**
 * Signals bridge-level failures before the decode result can be mapped into
 * application models.
 */
public class NativeWiresharkBridgeException extends RuntimeException {

    public NativeWiresharkBridgeException(String message) {
        super(message);
    }

    public NativeWiresharkBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
