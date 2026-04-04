package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import com.example.procedure.infrastructure.decode.Text2PcapBuildTool;
import com.example.procedure.infrastructure.decode.bridge.build.Text2PcapBuildGateway;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NativePcapBuildToolTests {

    @Test
    void build_pcap_from_text2pcap_style_hexdump() throws Exception {
        NativePcapBuildTool tool = new NativePcapBuildTool();
        Path dir = Files.createTempDirectory("native-pcap-test");
        Path hexdump = dir.resolve("payload.txt");
        Path out = dir.resolve("payload.pcap");

        Files.writeString(
                hexdump,
                "0000  01 02 03 04 05 06 07 08\n0008  09 0a 0b 0c\n",
                StandardCharsets.US_ASCII
        );

        Path built = tool.buildPcap(hexdump, 147, out);
        assertEquals(out, built);

        byte[] file = Files.readAllBytes(out);
        assertEquals(24 + 16 + 12, file.length);

        ByteBuffer header = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0xa1b2c3d4, header.getInt());
        assertEquals(2, Short.toUnsignedInt(header.getShort()));
        assertEquals(4, Short.toUnsignedInt(header.getShort()));
        header.getInt();
        header.getInt();
        assertEquals(65535, header.getInt());
        assertEquals(147, header.getInt());

        int tsSec = header.getInt();
        int tsUsec = header.getInt();
        int inclLen = header.getInt();
        int origLen = header.getInt();
        org.junit.jupiter.api.Assertions.assertTrue(tsSec > 0);
        assertEquals(12, inclLen);
        assertEquals(12, origLen);
        org.junit.jupiter.api.Assertions.assertTrue(tsUsec >= 0);

        byte[] payload = new byte[12];
        header.get(payload);
        assertArrayEquals(
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                payload
        );
    }

    @Test
    void gateway_prefers_native_builder_in_native_mode() throws Exception {
        NativePcapBuildTool nativeBuilder = mock(NativePcapBuildTool.class);
        Text2PcapBuildTool externalBuilder = mock(Text2PcapBuildTool.class);
        Text2PcapBuildGateway gateway = new Text2PcapBuildGateway(
                nativeBuilder,
                externalBuilder,
                "native",
                true
        );

        Path hexdump = Path.of("payload.txt");
        Path pcap = Path.of("payload.pcap");

        org.mockito.Mockito.when(nativeBuilder.buildPcap(hexdump, 151, pcap)).thenReturn(pcap);

        Path out = gateway.buildPcap(hexdump, 151, pcap);

        assertEquals(pcap, out);
        verify(nativeBuilder).buildPcap(hexdump, 151, pcap);
        verify(externalBuilder, never()).buildPcap(hexdump, 151, pcap);
    }
}
