package com.example.procedure.infrastructure.decode.nativews;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JNI-backed phase-1 bridge client. The current implementation stabilizes the
 * loading contract first; the native shim can later delegate into the C bridge.
 */
public class NativeWiresharkJniBridgeClient implements NativeWiresharkBridgeClient {

    private final NativeWiresharkBridgeProperties properties;
    private final NativeLibraryLoadStrategy loadStrategy;
    private final NativeWiresharkJniBindings bindings;
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public NativeWiresharkJniBridgeClient(NativeWiresharkBridgeProperties properties) {
        this(properties, new SystemNativeLibraryLoadStrategy(), new JniBindingsAdapter());
    }

    public NativeWiresharkJniBridgeClient(
            NativeWiresharkBridgeProperties properties,
            NativeLibraryLoadStrategy loadStrategy,
            NativeWiresharkJniBindings bindings
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.loadStrategy = Objects.requireNonNull(loadStrategy, "loadStrategy must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
    }

    @Override
    public String decodeNas5gs(NativeWiresharkNasRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NAS payload must not be empty");
        }
        ensureLoaded();
        try {
            return bindings.decodeNas5gsJson(
                    request.getPayload(),
                    request.isIncludeFieldTree(),
                    request.isIncludeOffsets()
            );
        } catch (UnsatisfiedLinkError e) {
            throw new NativeWiresharkBridgeException("JNI bridge method is not available yet", e);
        }
    }

    private void ensureLoaded() {
        if (loaded.get()) {
            return;
        }
        synchronized (loaded) {
            if (loaded.get()) {
                return;
            }
            Path jniPath = properties.jniLibraryPathOrNull();
            Path corePath = properties.coreLibraryPathOrNull();
            if (jniPath == null) {
                throw new NativeWiresharkBridgeException("JNI bridge library path is not configured");
            }
            if (!Files.exists(jniPath)) {
                throw new NativeWiresharkBridgeException("JNI bridge library does not exist: " + jniPath);
            }
            if (corePath != null && Files.exists(corePath)) {
                try {
                    loadStrategy.load(corePath);
                } catch (UnsatisfiedLinkError e) {
                    throw new NativeWiresharkBridgeException("Failed to load core bridge library: " + corePath, e);
                }
            }
            try {
                loadStrategy.load(jniPath);
                loaded.set(true);
            } catch (UnsatisfiedLinkError e) {
                throw new NativeWiresharkBridgeException("Failed to load JNI bridge library: " + jniPath, e);
            }
        }
    }

    public interface NativeLibraryLoadStrategy {
        void load(Path absoluteLibraryPath);
    }

    public interface NativeWiresharkJniBindings {
        String decodeNas5gsJson(byte[] payload, boolean includeFieldTree, boolean includeOffsets);
    }

    private static final class SystemNativeLibraryLoadStrategy implements NativeLibraryLoadStrategy {
        @Override
        public void load(Path absoluteLibraryPath) {
            System.load(absoluteLibraryPath.toString());
        }
    }

    private static final class JniBindingsAdapter implements NativeWiresharkJniBindings {
        @Override
        public String decodeNas5gsJson(byte[] payload, boolean includeFieldTree, boolean includeOffsets) {
            return NativeMethods.decodeNas5gsJson(payload, includeFieldTree, includeOffsets);
        }
    }

    private static final class NativeMethods {
        private NativeMethods() {
        }

        private static native String decodeNas5gsJson(byte[] payload, boolean includeFieldTree, boolean includeOffsets);
    }
}
