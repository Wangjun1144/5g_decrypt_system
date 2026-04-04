package com.example.procedure.infrastructure.capture;

import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Offline reader for classic libpcap files.
 *
 * <p>This is intentionally scoped to the wiretap-style "open + sequential read"
 * responsibility only. It does not perform protocol dissection.</p>
 */
@Component
public class PcapFileReader implements CaptureReader {

    private static final byte[] MAGIC_MICRO_BE = new byte[]{(byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4};
    private static final byte[] MAGIC_MICRO_LE = new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1};
    private static final byte[] MAGIC_NANO_BE = new byte[]{(byte) 0xa1, (byte) 0xb2, 0x3c, 0x4d};
    private static final byte[] MAGIC_NANO_LE = new byte[]{0x4d, 0x3c, (byte) 0xb2, (byte) 0xa1};

    @Override
    public boolean supports(Path capture) throws IOException {
        if (capture == null || !Files.exists(capture)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(capture)) {
            byte[] magic = readExact(in, 4);
            return matches(magic, MAGIC_MICRO_BE)
                    || matches(magic, MAGIC_MICRO_LE)
                    || matches(magic, MAGIC_NANO_BE)
                    || matches(magic, MAGIC_NANO_LE);
        } catch (EOFException ex) {
            return false;
        }
    }

    @Override
    public void read(Path capture, Consumer<CapturedPacket> consumer) throws IOException {
        if (capture == null || !Files.exists(capture)) {
            throw new IllegalArgumentException("capture file not found: " + capture);
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }

        try (InputStream in = Files.newInputStream(capture)) {
            PcapHeaderFormat format = PcapHeaderFormat.detect(readExact(in, 4));
            int versionMajor = readUnsignedShort(in, format.littleEndian);
            int versionMinor = readUnsignedShort(in, format.littleEndian);
            readUnsignedInt(in, format.littleEndian);
            readUnsignedInt(in, format.littleEndian);
            readUnsignedInt(in, format.littleEndian);
            int linkType = (int) readUnsignedInt(in, format.littleEndian);

            if (versionMajor != 2 || versionMinor != 4) {
                throw new IOException("Unsupported pcap version: " + versionMajor + "." + versionMinor);
            }

            long packetIndex = 0;
            long offset = 24;
            while (true) {
                byte[] packetHeader = tryReadExact(in, 16);
                if (packetHeader == null) {
                    return;
                }

                long tsSec = readUnsignedInt(packetHeader, 0, format.littleEndian);
                long tsFraction = readUnsignedInt(packetHeader, 4, format.littleEndian);
                int inclLen = (int) readUnsignedInt(packetHeader, 8, format.littleEndian);
                int origLen = (int) readUnsignedInt(packetHeader, 12, format.littleEndian);
                if (inclLen < 0 || origLen < 0) {
                    throw new IOException("Negative packet length in pcap header");
                }

                long dataOffset = offset + 16;
                byte[] payload = readExact(in, inclLen);
                consumer.accept(new CapturedPacket(
                        ++packetIndex,
                        format.toInstant(tsSec, tsFraction),
                        linkType,
                        inclLen,
                        origLen,
                        payload,
                        capture.toAbsolutePath().normalize(),
                        dataOffset
                ));
                offset = dataOffset + inclLen;
            }
        }
    }

    private static int readUnsignedShort(InputStream in, boolean littleEndian) throws IOException {
        byte[] bytes = readExact(in, 2);
        if (littleEndian) {
            return ((bytes[1] & 0xff) << 8) | (bytes[0] & 0xff);
        }
        return ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
    }

    private static long readUnsignedInt(InputStream in, boolean littleEndian) throws IOException {
        return readUnsignedInt(readExact(in, 4), 0, littleEndian);
    }

    private static long readUnsignedInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return ((long) bytes[offset] & 0xff)
                    | (((long) bytes[offset + 1] & 0xff) << 8)
                    | (((long) bytes[offset + 2] & 0xff) << 16)
                    | (((long) bytes[offset + 3] & 0xff) << 24);
        }
        return (((long) bytes[offset] & 0xff) << 24)
                | (((long) bytes[offset + 1] & 0xff) << 16)
                | (((long) bytes[offset + 2] & 0xff) << 8)
                | ((long) bytes[offset + 3] & 0xff);
    }

    private static byte[] tryReadExact(InputStream in, int length) throws IOException {
        byte[] out = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(out, read, length - read);
            if (n < 0) {
                if (read == 0) {
                    return null;
                }
                throw new EOFException("Unexpected EOF while reading pcap record");
            }
            read += n;
        }
        return out;
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] out = tryReadExact(in, length);
        if (out == null) {
            throw new EOFException("Unexpected EOF while reading pcap header");
        }
        return out;
    }

    private static boolean matches(byte[] left, byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        return true;
    }

    private enum TimestampPrecision {
        MICROSECONDS,
        NANOSECONDS
    }

    private record PcapHeaderFormat(boolean littleEndian, TimestampPrecision precision) {

        static PcapHeaderFormat detect(byte[] magic) throws IOException {
            if (matches(magic, MAGIC_MICRO_BE)) {
                return new PcapHeaderFormat(false, TimestampPrecision.MICROSECONDS);
            }
            if (matches(magic, MAGIC_MICRO_LE)) {
                return new PcapHeaderFormat(true, TimestampPrecision.MICROSECONDS);
            }
            if (matches(magic, MAGIC_NANO_BE)) {
                return new PcapHeaderFormat(false, TimestampPrecision.NANOSECONDS);
            }
            if (matches(magic, MAGIC_NANO_LE)) {
                return new PcapHeaderFormat(true, TimestampPrecision.NANOSECONDS);
            }
            throw new IOException("Unsupported pcap magic header");
        }

        Instant toInstant(long seconds, long fraction) {
            long nanos = precision == TimestampPrecision.MICROSECONDS ? fraction * 1_000L : fraction;
            return Instant.ofEpochSecond(seconds, nanos);
        }
    }
}
