package com.example.procedure.infrastructure.decode.nativews;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Direct JNA client for the minimal ws-core NAS/NGAP DLLs.
 */
@Component
public class DirectNativeWiresharkDllClient {

    private final NativeWiresharkBridgeProperties properties;
    private final AtomicBoolean nasLoaded = new AtomicBoolean(false);
    private final AtomicBoolean ngapLoaded = new AtomicBoolean(false);
    private final AtomicBoolean nrRrcLoaded = new AtomicBoolean(false);
    private final AtomicBoolean macNrChainLoaded = new AtomicBoolean(false);

    private volatile WsCoreNasLibrary nasLibrary;
    private volatile WsCoreNgapLibrary ngapLibrary;
    private volatile WsCoreNrRrcLibrary nrRrcLibrary;
    private volatile WsCoreMacNrChainLibrary macNrChainLibrary;
    private final AtomicBoolean windowsDllDirectoriesConfigured = new AtomicBoolean(false);

    public DirectNativeWiresharkDllClient(NativeWiresharkBridgeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public String decodeNas5gs(byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        NativeWiresharkNasRequest request = new NativeWiresharkNasRequest(
                payload,
                properties.isIncludeFieldTree(),
                properties.isIncludeOffsets()
        );
        return decodeNas5gs(request);
    }

    public String decodeNas5gsHex(String hex) {
        return decodeNas5gs(hexToBytes(hex));
    }

    public String decodeNas5gs(NativeWiresharkNasRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NAS payload must not be empty");
        }
        ensureNasLoaded();
        return decodeWithLibrary(
                request.getPayload(),
                request.isIncludeFieldTree(),
                request.isIncludeOffsets(),
                (decodeRequest, result) -> nasLibrary.ws_native_decode_nas_5gs(decodeRequest, result),
                result -> nasLibrary.ws_native_free_result(result)
        );
    }

    public String decodeNgap(byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        NativeWiresharkNgapRequest request = new NativeWiresharkNgapRequest(
                payload,
                properties.isIncludeFieldTree(),
                properties.isIncludeOffsets()
        );
        return decodeNgap(request);
    }

    public String decodeNgapHex(String hex) {
        return decodeNgap(hexToBytes(hex));
    }

    public String decodeNgap(NativeWiresharkNgapRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NGAP payload must not be empty");
        }
        ensureNgapLoaded();
        return decodeWithLibrary(
                request.getPayload(),
                request.isIncludeFieldTree(),
                request.isIncludeOffsets(),
                (decodeRequest, result) -> ngapLibrary.ws_native_decode_ngap(decodeRequest, result),
                result -> ngapLibrary.ws_native_free_result(result)
        );
    }

    public String decodeNrRrc(byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        NativeWiresharkNrRrcRequest request = new NativeWiresharkNrRrcRequest(
                payload,
                properties.isIncludeFieldTree(),
                properties.isIncludeOffsets()
        );
        return decodeNrRrc(request);
    }

    public String decodeNrRrcHex(String hex) {
        return decodeNrRrc(hexToBytes(hex));
    }

    public String decodeNrRrc(NativeWiresharkNrRrcRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NR-RRC payload must not be empty");
        }
        ensureNrRrcLoaded();
        return decodeWithLibrary(
                request.getPayload(),
                request.isIncludeFieldTree(),
                request.isIncludeOffsets(),
                (decodeRequest, result) -> nrRrcLibrary.ws_native_decode_nr_rrc(decodeRequest, result),
                result -> nrRrcLibrary.ws_native_free_result(result)
        );
    }

    public String decodeMacNrChain(byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        NativeWiresharkMacNrRequest request = new NativeWiresharkMacNrRequest(
                payload,
                properties.isIncludeFieldTree(),
                properties.isIncludeOffsets()
        );
        return decodeMacNrChain(request);
    }

    public String decodeMacNrChainHex(String hex) {
        return decodeMacNrChain(hexToBytes(hex));
    }

    public String decodeMacNrChain(NativeWiresharkMacNrRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("MAC-NR chain payload must not be empty");
        }
        ensureMacNrChainLoaded();
        return decodeWithLibrary(
                request.getPayload(),
                request.isIncludeFieldTree(),
                request.isIncludeOffsets(),
                (decodeRequest, result) -> macNrChainLibrary.ws_native_decode_mac_nr(decodeRequest, result),
                result -> macNrChainLibrary.ws_native_free_result(result)
        );
    }

    private void ensureNasLoaded() {
        if (nasLoaded.get()) {
            return;
        }
        synchronized (nasLoaded) {
            if (nasLoaded.get()) {
                return;
            }
            Path libraryPath = requireExisting(properties.nasMinimalLibraryPathOrNull(), "NAS minimal DLL path is not configured");
            preloadRuntimeLibraries();
            nasLibrary = Native.load(libraryPath.toString(), WsCoreNasLibrary.class);
            nasLoaded.set(true);
        }
    }

    private void ensureNgapLoaded() {
        if (ngapLoaded.get()) {
            return;
        }
        synchronized (ngapLoaded) {
            if (ngapLoaded.get()) {
                return;
            }
            Path libraryPath = requireExisting(properties.ngapMinimalLibraryPathOrNull(), "NGAP minimal DLL path is not configured");
            preloadRuntimeLibraries();
            ngapLibrary = Native.load(libraryPath.toString(), WsCoreNgapLibrary.class);
            ngapLoaded.set(true);
        }
    }

    private void ensureNrRrcLoaded() {
        if (nrRrcLoaded.get()) {
            return;
        }
        synchronized (nrRrcLoaded) {
            if (nrRrcLoaded.get()) {
                return;
            }
            Path libraryPath = requireExisting(properties.nrRrcMinimalLibraryPathOrNull(), "NR-RRC minimal DLL path is not configured");
            preloadRuntimeLibraries();
            nrRrcLibrary = Native.load(libraryPath.toString(), WsCoreNrRrcLibrary.class);
            nrRrcLoaded.set(true);
        }
    }

    private void ensureMacNrChainLoaded() {
        if (macNrChainLoaded.get()) {
            return;
        }
        synchronized (macNrChainLoaded) {
            if (macNrChainLoaded.get()) {
                return;
            }
            Path libraryPath = requireExisting(properties.macNrChainMinimalLibraryPathOrNull(), "MAC-NR chain minimal DLL path is not configured");
            preloadRuntimeLibraries();
            macNrChainLibrary = Native.load(libraryPath.toString(), WsCoreMacNrChainLibrary.class);
            macNrChainLoaded.set(true);
        }
    }

    private void preloadRuntimeLibraries() {
        configureWindowsDllDirectories();
        for (Path dir : properties.additionalRuntimeLibraryDirsOrEmpty()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            loadIfPresent(dir.resolve("libgcc_s_sjlj-1.dll"));
            loadIfPresent(dir.resolve("libwinpthread-1.dll"));
            loadIfPresent(dir.resolve("libasprintf-0.dll"));
            loadIfPresent(dir.resolve("libiconv-2.dll"));
            loadIfPresent(dir.resolve("libintl-8.dll"));
            loadIfPresent(dir.resolve("libpcre2-8-0.dll"));
            loadIfPresent(dir.resolve("libglib-2.0-0.dll"));
            loadIfPresent(dir.resolve("libgmodule-2.0-0.dll"));
            loadIfPresent(dir.resolve("libgobject-2.0-0.dll"));
            loadIfPresent(dir.resolve("libgthread-2.0-0.dll"));
        }
    }

    private void configureWindowsDllDirectories() {
        if (!isWindows()) {
            return;
        }
        if (windowsDllDirectoriesConfigured.get()) {
            return;
        }
        synchronized (windowsDllDirectoriesConfigured) {
            if (windowsDllDirectoriesConfigured.get()) {
                return;
            }
            try {
                Kernel32Library kernel32 = com.sun.jna.Native.load("kernel32", Kernel32Library.class);
                kernel32.SetDefaultDllDirectories(Kernel32Library.LOAD_LIBRARY_SEARCH_DEFAULT_DIRS
                        | Kernel32Library.LOAD_LIBRARY_SEARCH_USER_DIRS);
                for (Path dir : properties.additionalRuntimeLibraryDirsOrEmpty()) {
                    if (Files.isDirectory(dir)) {
                        kernel32.AddDllDirectory(dir.toAbsolutePath().normalize().toString());
                    }
                }
            } catch (UnsatisfiedLinkError ignored) {
                // Fall back to explicit System.load preloading below.
            }
            windowsDllDirectoriesConfigured.set(true);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void loadIfPresent(Path libraryPath) {
        if (libraryPath == null || !Files.exists(libraryPath)) {
            return;
        }
        try {
            System.load(libraryPath.toAbsolutePath().normalize().toString());
        } catch (UnsatisfiedLinkError ignored) {
            // Ignore duplicates or non-loadable optional runtime DLLs here.
        }
    }

    private static Path requireExisting(Path libraryPath, String missingMessage) {
        if (libraryPath == null) {
            throw new NativeWiresharkBridgeException(missingMessage);
        }
        if (!Files.exists(libraryPath)) {
            throw new NativeWiresharkBridgeException("Native DLL does not exist: " + libraryPath);
        }
        return libraryPath;
    }

    private static String decodeWithLibrary(
            byte[] payload,
            boolean includeFieldTree,
            boolean includeOffsets,
            NativeDecoder decoder,
            NativeResultReleaser releaser
    ) {
        Memory payloadMemory = new Memory(payload.length);
        payloadMemory.write(0, payload, 0, payload.length);

        WsNativeDecodeRequest decodeRequest = new WsNativeDecodeRequest();
        decodeRequest.payload = payloadMemory;
        decodeRequest.payload_length = payload.length;
        decodeRequest.include_field_tree = includeFieldTree ? 1 : 0;
        decodeRequest.include_offsets = includeOffsets ? 1 : 0;
        decodeRequest.write();

        WsNativeDecodeResult result = new WsNativeDecodeResult();
        result.write();

        int returnCode = decoder.decode(decodeRequest, result);
        result.read();
        try {
            if (returnCode != 0 || result.status_code != 0) {
                String error = result.error_utf8 == null ? null : result.error_utf8.getString(0, "UTF-8");
                throw new NativeWiresharkBridgeException("Native decode failed: " + (error == null ? "unknown error" : error));
            }
            if (result.json_utf8 == null) {
                throw new NativeWiresharkBridgeException("Native decode returned no JSON");
            }
            return result.json_utf8.getString(0, "UTF-8");
        } finally {
            releaser.release(result);
        }
    }

    private static byte[] hexToBytes(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        String normalized = hex.replaceAll("\\s+", "");
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("hex length must be even");
        }
        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
        }
        return bytes;
    }

    @FunctionalInterface
    private interface NativeDecoder {
        int decode(WsNativeDecodeRequest request, WsNativeDecodeResult result);
    }

    @FunctionalInterface
    private interface NativeResultReleaser {
        void release(WsNativeDecodeResult result);
    }

    private interface WsCoreNasLibrary extends Library {
        int ws_native_decode_nas_5gs(WsNativeDecodeRequest request, WsNativeDecodeResult result);

        void ws_native_free_result(WsNativeDecodeResult result);
    }

    private interface WsCoreNgapLibrary extends Library {
        int ws_native_decode_ngap(WsNativeDecodeRequest request, WsNativeDecodeResult result);

        void ws_native_free_result(WsNativeDecodeResult result);
    }

    private interface WsCoreNrRrcLibrary extends Library {
        int ws_native_decode_nr_rrc(WsNativeDecodeRequest request, WsNativeDecodeResult result);

        void ws_native_free_result(WsNativeDecodeResult result);
    }

    private interface WsCoreMacNrChainLibrary extends Library {
        int ws_native_decode_mac_nr(WsNativeDecodeRequest request, WsNativeDecodeResult result);

        void ws_native_free_result(WsNativeDecodeResult result);
    }

    private interface Kernel32Library extends StdCallLibrary {
        int LOAD_LIBRARY_SEARCH_DEFAULT_DIRS = 0x00001000;
        int LOAD_LIBRARY_SEARCH_USER_DIRS = 0x00000400;

        boolean SetDefaultDllDirectories(int directoryFlags);

        Pointer AddDllDirectory(String newDirectory);
    }

    @Structure.FieldOrder({"payload", "payload_length", "include_field_tree", "include_offsets"})
    public static class WsNativeDecodeRequest extends Structure {
        public Pointer payload;
        public long payload_length;
        public int include_field_tree;
        public int include_offsets;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("payload", "payload_length", "include_field_tree", "include_offsets");
        }
    }

    @Structure.FieldOrder({"status_code", "json_utf8", "error_utf8"})
    public static class WsNativeDecodeResult extends Structure {
        public int status_code;
        public Pointer json_utf8;
        public Pointer error_utf8;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("status_code", "json_utf8", "error_utf8");
        }
    }
}
