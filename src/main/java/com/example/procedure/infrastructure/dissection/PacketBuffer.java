package com.example.procedure.infrastructure.dissection;

import java.util.Arrays;

/**
 * Lightweight byte-slice wrapper inspired by Wireshark's tvbuff abstraction.
 */
public final class PacketBuffer {

    private final byte[] bytes;
    private final int offset;
    private final int length;

    private PacketBuffer(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
    }

    public static PacketBuffer wrap(byte[] bytes) {
        byte[] safe = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        return new PacketBuffer(safe, 0, safe.length);
    }

    public int length() {
        return length;
    }

    public int remaining(int relativeOffset) {
        if (relativeOffset < 0) {
            throw new IllegalArgumentException("relativeOffset must not be negative");
        }
        return Math.max(0, length - relativeOffset);
    }

    public int getU8(int relativeOffset) {
        requireRange(relativeOffset, 1);
        return bytes[offset + relativeOffset] & 0xff;
    }

    public int getU16(int relativeOffset) {
        requireRange(relativeOffset, 2);
        return ((bytes[offset + relativeOffset] & 0xff) << 8)
                | (bytes[offset + relativeOffset + 1] & 0xff);
    }

    public long getU32(int relativeOffset) {
        requireRange(relativeOffset, 4);
        return ((long) (bytes[offset + relativeOffset] & 0xff) << 24)
                | ((long) (bytes[offset + relativeOffset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + relativeOffset + 2] & 0xff) << 8)
                | ((long) (bytes[offset + relativeOffset + 3] & 0xff));
    }

    public PacketBuffer slice(int relativeOffset) {
        return slice(relativeOffset, length - relativeOffset);
    }

    public PacketBuffer slice(int relativeOffset, int sliceLength) {
        requireRange(relativeOffset, sliceLength);
        return new PacketBuffer(bytes, offset + relativeOffset, sliceLength);
    }

    public byte[] toByteArray() {
        return Arrays.copyOfRange(bytes, offset, offset + length);
    }

    private void requireRange(int relativeOffset, int requestedLength) {
        if (relativeOffset < 0 || requestedLength < 0 || relativeOffset + requestedLength > length) {
            throw new IndexOutOfBoundsException(
                    "Invalid packet buffer access: offset=" + relativeOffset
                            + ", length=" + requestedLength
                            + ", available=" + length
            );
        }
    }
}
