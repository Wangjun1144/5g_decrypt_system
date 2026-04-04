package com.example.procedure.infrastructure.capture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wiretap-style boundary for offline capture readers.
 *
 * <p>Implementations decide whether they can open one capture file and then
 * expose packet records through a sequential read routine.</p>
 */
public interface CaptureReader {

    /**
     * Returns whether this reader can open the given capture file.
     */
    boolean supports(Path capture) throws IOException;

    /**
     * Reads one capture file sequentially and emits packets to the consumer.
     */
    void read(Path capture, Consumer<CapturedPacket> consumer) throws IOException;

    /**
     * Convenience helper for tests and diagnostics.
     */
    default List<CapturedPacket> readAll(Path capture) throws IOException {
        List<CapturedPacket> packets = new ArrayList<>();
        read(capture, packets::add);
        return packets;
    }
}
