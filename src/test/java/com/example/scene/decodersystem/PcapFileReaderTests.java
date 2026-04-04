package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.capture.CapturedPacket;
import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcapFileReaderTests {

    @Test
    void should_read_packet_written_by_native_pcap_builder() throws Exception {
        HexCodec hexCodec = new HexCodec();
        NativePcapBuildTool builder = new NativePcapBuildTool();
        PcapFileReader reader = new PcapFileReader();

        String plainHex = "0102030405060708090a0b0c";
        byte[] expectedPayload = hexCodec.decodeHex(plainHex);

        Path dir = Files.createTempDirectory("pcap-reader-native");
        Path hexdump = dir.resolve("payload.txt");
        Path pcap = dir.resolve("payload.pcap");
        Files.writeString(
                hexdump,
                hexCodec.toText2PcapHexdump(expectedPayload),
                StandardCharsets.US_ASCII
        );
        builder.buildPcap(hexdump, 151, pcap);

        assertTrue(reader.supports(pcap));

        List<CapturedPacket> packets = reader.readAll(pcap);
        assertEquals(1, packets.size());

        CapturedPacket packet = packets.get(0);
        assertEquals(1L, packet.getPacketIndex());
        assertEquals(151, packet.getLinkType());
        assertEquals(expectedPayload.length, packet.getCapturedLength());
        assertEquals(expectedPayload.length, packet.getOriginalLength());
        assertArrayEquals(expectedPayload, packet.getRawBytes());
        assertTrue(packet.getDataOffset() >= 40);
    }

    @Test
    void should_read_real_project_pcap_sample() throws Exception {
        PcapFileReader reader = new PcapFileReader();
        Path pcap = Path.of("gnb_capture.pcap");

        assertTrue(Files.exists(pcap), "project sample pcap should exist");
        assertTrue(reader.supports(pcap), "reader should recognize classic pcap sample");

        List<CapturedPacket> packets = reader.readAll(pcap);
        assertFalse(packets.isEmpty(), "sample pcap should contain at least one packet");

        CapturedPacket first = packets.get(0);
        assertEquals(1L, first.getPacketIndex());
        assertTrue(first.getCapturedLength() > 0);
        assertTrue(first.getOriginalLength() >= first.getCapturedLength());
        assertTrue(first.getRawBytes().length > 0);
        assertTrue(first.getLinkType() >= 0);
    }
}
