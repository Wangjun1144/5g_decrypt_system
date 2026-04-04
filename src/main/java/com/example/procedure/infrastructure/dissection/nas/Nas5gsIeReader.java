package com.example.procedure.infrastructure.dissection.nas;

import com.example.procedure.infrastructure.dissection.PacketBuffer;

/**
 * Small NAS reader utility used by structured message dissectors.
 */
public final class Nas5gsIeReader {

    private final PacketBuffer buffer;

    public Nas5gsIeReader(PacketBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        this.buffer = buffer;
    }

    public int u8(int offset) {
        return buffer.getU8(offset);
    }

    public int u16(int offset) {
        return buffer.getU16(offset);
    }

    public PacketBuffer slice(int offset, int length) {
        return buffer.slice(offset, length);
    }

    public int remaining(int offset) {
        return buffer.remaining(offset);
    }
}
