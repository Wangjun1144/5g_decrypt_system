package com.example.procedure.wireshark;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class HexCodec {

    public byte[] decodeHex(String hex) {
        if (hex == null) return new byte[0];
        String s = hex.replaceAll("[:\\s]", "").toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return new byte[0];
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("Hex length must be even: " + s.length());

        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
                int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid hex at index " + (i * 2));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public String toText2PcapHexdump(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int offset = 0; offset < bytes.length; offset += 16) {
            sb.append(String.format("%04x  ", offset));
            int n = Math.min(16, bytes.length - offset);
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%02x", bytes[offset + i] & 0xff));
                if (i != n - 1) sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
