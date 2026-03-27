package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.application.message.SignalingMessagePipeline;
import com.example.procedure.infrastructure.decode.bridge.pcap.PcapParseBridgeService;
import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugDecodeArtifacts;
import com.example.procedure.infrastructure.decode.bridge.plaintext.PlaintextDecodeRequest;
import com.example.procedure.infrastructure.decode.bridge.plaintext.PlaintextPacketParseBridgeService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.SignalingMessagePrinter;
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
    private SignalingMessagePipeline signalingMessagePipeline;

    /**
     * 澶嶇敤浣犵幇鏈夌殑澶勭悊鍏ュ彛椋庢牸锛?
     * 鍏堜氦缁?ueIdBinder锛屽啀杩涘叆 messageProcessingService
     */
    /**
     * Reuse the formal ingress pipeline so bridge tests follow the same entry path as production.
     */
    private void processOne(SignalingMessage msg) {
        // Bridge tests now enter through the formal pipeline so binding and main processing
        // follow the same ingress path as production traffic.
        signalingMessagePipeline.process(msg);

        System.out.println("=== processOne ===");
        System.out.println("msgId     = " + msg.getMsgId());
        System.out.println("ueId      = " + msg.getUeId());
        System.out.println("iface     = " + msg.getIface());
        System.out.println("direction = " + msg.getDirection());
        System.out.println("layer     = " + msg.getProtocolLayer());
        System.out.println("msgType   = " + msg.getMsgType());

        SignalingMessagePrinter.printAndWriteToFile(
                msg, Paths.get("logs/signaling_dump.log"), true
        );
    }

    /**
     * 杩欎釜鏂规硶鍙槸涓轰簡楠岃瘉锛氬凡鏈夌湡瀹?pcap 浠嶇劧鑳借蛋缁熶竴妗?
     */
    private void processPcap(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        pcapParseBridgeService.parsePcap(pcap, wanted, enabledRaw, this::processOne);
    }

    /**
     * 娴嬭瘯1锛?
     * 鏄庢枃 hex -> 璋冭瘯钀界洏 pcap/json -> 鍐嶈繘鍏ョ粺涓€瑙ｆ瀽閾?
     *
     * 閫傚悎浣犲厛鐪嬶細
     * - pcap 鏈夋病鏈夌敓鎴?
     * - json 鏈夋病鏈夌敓鎴?
     * - processOne 鏈夋病鏈夎瑙﹀彂
     */
    @Test
    void debug_build_parse_rrc_plaintext() throws Exception {
        String plainHex =
                "7e00670100492e0112c1ffff932801007b003c8080211001000010810600000000830600000000000d00000300000100000c00001200000200000a0000050000100000110000170101002300002400120181250403696d73";

        Set<String> wanted = Set.of(
                "nas-5gs_raw", "nas-5gs", "nr-rrc",
                "mac-nr", "mac-nr_raw", "ngap", "http2", "json.object"
        );
        Set<String> enabledRaw = Set.of("nas-5gs_raw", "mac-nr_raw");

        PlaintextDecodeRequest req = new PlaintextDecodeRequest();
        req.setPlainHex(plainHex);
        req.setProtocolHint("NAS_5GS");
        req.setTraceId("trace_debug_17NAS");
        req.setSourceMsgId("msg_debug_17RRC");
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
     * 娴嬭瘯2锛?
     * 鏄庢枃 hex -> 涓存椂 pcap -> 鐩存帴娴佸紡杩涘叆缁熶竴瑙ｆ瀽閾?
     *
     * 閫傚悎浣犻獙璇佹寮忎娇鐢ㄨ矾寰勩€?
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
     * 娴嬭瘯3锛堝彲閫夛級锛?
     * 楠岃瘉鐪熷疄 pcap 涔熻兘澶嶇敤缁熶竴妗ャ€?
     * 浣犳湁鏂囦欢鏃跺啀鎵撳紑杩欎釜娴嬭瘯銆?
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
