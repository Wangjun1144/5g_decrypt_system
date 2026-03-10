package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.decodebridge.DebugDecodeArtifacts;
import com.example.procedure.decodebridge.PcapParseBridgeService;
import com.example.procedure.decodebridge.PlaintextDecodeRequest;
import com.example.procedure.decodebridge.PlaintextPacketParseBridgeService;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;

import com.example.procedure.rule.UeIdBinder;
import com.example.procedure.service.MsgProcessing_Service;
import com.example.procedure.util.SignalingMessagePrinter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class PlaintextPacketParseBridgeIT {

    @Autowired
    private PlaintextPacketParseBridgeService plaintextPacketParseBridgeService;

    @Autowired
    private PcapParseBridgeService pcapParseBridgeService;

    @Autowired
    private UeIdBinder ueIdBinder;

    @Autowired
    private MsgProcessing_Service messageProcessingService;

    /**
     * 复用你现有的处理入口风格：
     * 先交给 ueIdBinder，再进入 messageProcessingService
     */
    private void processOne(SignalingMessage msg) {
        ueIdBinder.handle(msg, m -> {
            MessageProcessingResult result = messageProcessingService.process(m);

            System.out.println("=== processOne ===");
            System.out.println("msgId     = " + m.getMsgId());
            System.out.println("ueId      = " + m.getUeId());
            System.out.println("iface     = " + m.getIface());
            System.out.println("direction = " + m.getDirection());
            System.out.println("layer     = " + m.getProtocolLayer());
            System.out.println("msgType   = " + m.getMsgType());
            System.out.println("result    = " + result);

            SignalingMessagePrinter.printAndWriteToFile(
                    m, Paths.get("logs/signaling_dump.log"), true
            );
        });
    }

    /**
     * 这个方法只是为了验证：已有真实 pcap 仍然能走统一桥
     */
    private void processPcap(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        pcapParseBridgeService.parsePcap(pcap, wanted, enabledRaw, this::processOne);
    }

    /**
     * 测试1：
     * 明文 hex -> 调试落盘 pcap/json -> 再进入统一解析链
     *
     * 适合你先看：
     * - pcap 有没有生成
     * - json 有没有生成
     * - processOne 有没有被触发
     */
    @Test
    void debug_build_parse_rrc_plaintext() throws Exception {
        String plainHex =
                "3a2fbf0121479913017ed421dbe7dc73430076d9da448b7d3f6931f4d55767c51" +
                        "ca845bef2ae228ac002e188cd69aee2521067a5ac225743b038cc92bd1f00b47" +
                        "c2ce60e64e2c87e39ef42c2a237c9ec508e3b85ea139f309fc691bf61c2836ad780";

        Set<String> wanted = Set.of(
                "nas-5gs_raw", "nas-5gs", "nr-rrc",
                "mac-nr", "mac-nr_raw", "ngap", "http2", "json.object"
        );
        Set<String> enabledRaw = Set.of("nas-5gs_raw", "mac-nr_raw");

        PlaintextDecodeRequest req = new PlaintextDecodeRequest();
        req.setPlainHex(plainHex);
        req.setProtocolHint("NR_RRC_UL_DCCH");
        req.setTraceId("trace_debug_plaintext");
        req.setSourceMsgId("msg_debug_plaintext");
        req.setUeId("UE_TEST_DEBUG");
        req.setKeepHexdumpFile(true);

        DebugDecodeArtifacts artifacts = plaintextPacketParseBridgeService.debugBuildAndParse(
                req,
                wanted,
                enabledRaw,
                this::processOne
        );

        System.out.println("=== debug_build_parse_rrc_plaintext done ===");
        System.out.println("workDir  = " + artifacts.getWorkDir().toAbsolutePath());
        System.out.println("hexdump  = " +
                (artifacts.getHexdumpFile() == null ? "deleted" : artifacts.getHexdumpFile().toAbsolutePath()));
        System.out.println("pcap     = " + artifacts.getPcapFile().toAbsolutePath());
        System.out.println("json     = " + artifacts.getJsonFile().toAbsolutePath());
        System.out.println("dlt      = " + artifacts.getDlt());
        System.out.println("byteSize = " + artifacts.getByteLength());
    }

    /**
     * 测试2：
     * 明文 hex -> 临时 pcap -> 直接流式进入统一解析链
     *
     * 适合你验证正式使用路径。
     */
    @Test
    void stream_build_parse_rrc_plaintext() throws Exception {
        String plainHex =
                "3a2fbf0121479913017ed421dbe7dc73430076d9da448b7d3f6931f4d55767c51" +
                        "ca845bef2ae228ac002e188cd69aee2521067a5ac225743b038cc92bd1f00b47" +
                        "c2ce60e64e2c87e39ef42c2a237c9ec508e3b85ea139f309fc691bf61c2836ad780";

        Set<String> wanted = Set.of(
                "nas-5gs_raw", "nas-5gs", "nr-rrc",
                "mac-nr", "mac-nr_raw", "ngap", "http2", "json.object"
        );
        Set<String> enabledRaw = Set.of("nas-5gs_raw", "mac-nr_raw");

        PlaintextDecodeRequest req = new PlaintextDecodeRequest();
        req.setPlainHex(plainHex);
        req.setProtocolHint("NR_RRC_UL_DCCH");
        req.setTraceId("trace_stream_plaintext");
        req.setSourceMsgId("msg_stream_plaintext");
        req.setUeId("UE_TEST_STREAM");

        plaintextPacketParseBridgeService.streamBuildAndParse(
                req,
                wanted,
                enabledRaw,
                this::processOne
        );

        System.out.println("=== stream_build_parse_rrc_plaintext done ===");
    }

    /**
     * 测试3（可选）：
     * 验证真实 pcap 也能复用统一桥。
     * 你有文件时再打开这个测试。
     */
    // @Test
    void process_existing_pcap_through_bridge() throws Exception {
        Path pcap = Path.of("gnb_capture.pcap");

        Set<String> wanted = Set.of(
                "nas-5gs_raw", "nas-5gs", "nr-rrc",
                "mac-nr", "mac-nr_raw", "ngap", "http2", "json.object"
        );
        Set<String> enabledRaw = Set.of("nas-5gs_raw", "mac-nr_raw");

        processPcap(pcap, wanted, enabledRaw);

        System.out.println("=== process_existing_pcap_through_bridge done ===");
    }
}