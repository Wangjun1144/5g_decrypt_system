package com.example.procedure.infrastructure.decode;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * hex 编解码支持组件。
 *
 * 当前定位：
 * 1. 这是 decode 基础设施层的通用工具
 * 2. 为 hexdump 构建、明文转 pcap、调试解码等流程提供统一 hex 能力
 * 3. 不再放在 wireshark 包下，避免把通用工具和配置/聚合能力混在一起
 */
@Component
public class HexCodec {

    /**
     * 将 hex 文本解码为字节数组。
     *
     * @param hex hex 文本
     * @return 解码后的字节数组
     */
    // REFACTOR STEP: WIRESHARK_ROLE_CONSOLIDATION
    public byte[] decodeHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }

        String normalized = hex.replaceAll("[:\\s]", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex length must be even: " + normalized.length());
        }

        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex at index " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /**
     * 将字节数组转换为 text2pcap 可读的 hexdump 文本。
     *
     * @param bytes 字节数组
     * @return hexdump 文本
     */
    // REFACTOR STEP: WIRESHARK_ROLE_CONSOLIDATION
    public String toText2PcapHexdump(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int offset = 0; offset < bytes.length; offset += 16) {
            sb.append(String.format("%04x  ", offset));
            int n = Math.min(16, bytes.length - offset);
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%02x", bytes[offset + i] & 0xff));
                if (i != n - 1) {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
