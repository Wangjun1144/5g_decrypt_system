package com.example.scene.decodersystem;

import com.example.procedure.infrastructure.capture.PcapFileReader;
import com.example.procedure.infrastructure.decode.HexCodec;
import com.example.procedure.infrastructure.decode.NativePcapBuildTool;
import com.example.procedure.infrastructure.decode.bridge.pcap.NativeNasPcapDecodeGateway;
import com.example.procedure.infrastructure.dissection.FrameDissector;
import com.example.procedure.infrastructure.dissection.assemble.NativeNasSignalingMessageAssembler;
import com.example.procedure.infrastructure.dissection.entry.Nas5gsEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NgapEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcDlCcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcDlDcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcUlCcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.NrRrcUlDcchEntryDissector;
import com.example.procedure.infrastructure.dissection.entry.UdpEntryDissector;
import com.example.procedure.infrastructure.dissection.registry.LinkTypeDissectorRegistry;
import com.example.procedure.infrastructure.dissection.registry.ProtocolDissectorRegistry;
import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pcap.PcapDecodeCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeNasPcapDecodeGatewayTests {

    @Test
    void should_decode_nas_pcap_into_signaling_message_list() throws Exception {
        HexCodec hexCodec = new HexCodec();
        NativePcapBuildTool builder = new NativePcapBuildTool();
        PcapFileReader reader = new PcapFileReader();

        WiresharkProperties props = new WiresharkProperties();
        Map<Integer, String> userDlts = new LinkedHashMap<>();
        userDlts.put(151, "nas-5gs");
        props.setUserDlts(userDlts);

        FrameDissector frameDissector = new FrameDissector(
                new LinkTypeDissectorRegistry(
                        new ProtocolDissectorRegistry(List.of(
                                new Nas5gsEntryDissector(),
                                new NgapEntryDissector(),
                                new UdpEntryDissector(),
                                new NrRrcUlDcchEntryDissector(),
                                new NrRrcDlDcchEntryDissector(),
                                new NrRrcUlCcchEntryDissector(),
                                new NrRrcDlCcchEntryDissector()
                        )),
                        props
                )
        );

        NativeNasPcapDecodeGateway gateway = new NativeNasPcapDecodeGateway(
                reader,
                frameDissector,
                new NativeNasSignalingMessageAssembler(new com.example.procedure.infrastructure.dissection.assemble.NativeNasInfoAssembler())
        );

        Path dir = Files.createTempDirectory("native-nas-gateway");
        Path hexdump = dir.resolve("reg.txt");
        Path pcap = dir.resolve("reg.pcap");
        Files.writeString(
                hexdump,
                hexCodec.toText2PcapHexdump(hexCodec.decodeHex("7e004101")),
                StandardCharsets.US_ASCII
        );
        builder.buildPcap(hexdump, 151, pcap);

        List<SignalingMessage> messages = new ArrayList<>();
        gateway.decode(PcapDecodeCommand.of(
                pcap,
                Set.of("nas-5gs"),
                Set.of(),
                messages::add
        ));

        assertEquals(1, messages.size());
        SignalingMessage message = messages.get(0);
        assertEquals("NAS", message.getProtocolLayer());
        assertEquals("Registration request", message.getMsgType());
        assertTrue(message.getNasList() != null && !message.getNasList().isEmpty());
        assertEquals("0x41", message.getNasList().get(0).getMmMessageType());
        assertEquals("1", message.getNasList().get(0).getRegType5gs());
    }

    @Test
    void should_ignore_request_when_nas_not_requested() throws Exception {
        HexCodec hexCodec = new HexCodec();
        NativePcapBuildTool builder = new NativePcapBuildTool();
        PcapFileReader reader = new PcapFileReader();

        WiresharkProperties props = new WiresharkProperties();
        props.setUserDlts(Map.of(151, "nas-5gs"));

        FrameDissector frameDissector = new FrameDissector(
                new LinkTypeDissectorRegistry(
                        new ProtocolDissectorRegistry(List.of(new Nas5gsEntryDissector())),
                        props
                )
        );

        NativeNasPcapDecodeGateway gateway = new NativeNasPcapDecodeGateway(
                reader,
                frameDissector,
                new NativeNasSignalingMessageAssembler(new com.example.procedure.infrastructure.dissection.assemble.NativeNasInfoAssembler())
        );

        Path dir = Files.createTempDirectory("native-nas-gateway-skip");
        Path hexdump = dir.resolve("reg.txt");
        Path pcap = dir.resolve("reg.pcap");
        Files.writeString(
                hexdump,
                hexCodec.toText2PcapHexdump(hexCodec.decodeHex("7e004101")),
                StandardCharsets.US_ASCII
        );
        builder.buildPcap(hexdump, 151, pcap);

        List<SignalingMessage> messages = new ArrayList<>();
        gateway.decode(PcapDecodeCommand.of(
                pcap,
                Set.of("rrc"),
                Set.of(),
                messages::add
        ));

        assertTrue(messages.isEmpty());
    }
}
