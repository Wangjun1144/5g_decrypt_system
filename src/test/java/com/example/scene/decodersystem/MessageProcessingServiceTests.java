package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.application.message.MessageApplicationService;
import com.example.procedure.application.message.SignalingMessagePipeline;
import com.example.procedure.application.pcap.PcapBatchProcessRequest;
import com.example.procedure.application.pcap.PcapBatchProcessor;
import com.example.procedure.model.initialaccess.*;
import com.example.procedure.model.*;
import com.example.procedure.model.message.MessagePayload;
import com.example.procedure.infrastructure.parser.TsharkJsonMessageParser;
import com.example.procedure.infrastructure.parser.streaming.layers.LayersSelectiveParser;
import com.example.procedure.infrastructure.parser.streaming.layers.StreamingMessageEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Broad scene-style tests around message processing, initial-access
 * progression, and pcap replay entry paths.
 *
 * This class still behaves more like an integration-style scenario suite than
 * a narrow unit test class, so this cleanup keeps its current shape and
 * focuses on readability rather than structural rewrites.
 */
@SpringBootTest(classes = Application.class)
class MessageProcessingServiceTests {

    @Autowired
    private MessageApplicationService messageApplicationService;
    @Autowired
    private SignalingMessagePipeline signalingMessagePipeline;

    @Autowired
    private PcapBatchProcessor pcapBatchProcessor;


    // ====== Common message builders ======
    private SignalingMessage buildMsg(String ueId,
                                      String iface,
                                      String direction,
                                      String protocolLayer,
                                      String msgType,
                                      MessagePayload payload) {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId(ueId);
        msg.setIface(iface);
        msg.setDirection(direction);
        msg.setProtocolLayer(protocolLayer);
        msg.setMsgType(msgType);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setFrameNo(1L);
        msg.setPayload(payload);
        return msg;
    }

    // ====== Convenience builders for initial-access-driving messages ======

    /** RRCSetupComplete + Registration request + IMSI + C-RNTI */
    private SignalingMessage rrcSetupCompleteStart(String ueId) {
        RrcSetupCompletePayload pl = new RrcSetupCompletePayload();
        pl.setHasRegistrationRequest(true);
        pl.setImsi(ueId);          // 娴嬭瘯閲屽亣瀹?ueId = IMSI
        pl.setCrnti("0x1234");

        return buildMsg(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRCSetupComplete",
                pl
        );
    }

    /** Initial UE Message + Registration request + RAN UE NGAP ID + NCGI */
    private SignalingMessage initialUeMessageStart(String ueId) {
        NgapInitialUeMessagePayload pl = new NgapInitialUeMessagePayload();
        pl.setHasRegistrationRequest(true);
        pl.setRanUeNgapId("RAN-UE-1");
        pl.setNcgi("NCGI-1");

        return buildMsg(
                ueId,
                "N2",
                "UL",
                "NGAP",
                "Initial UE Message",
                pl
        );
    }

    /** Nausf_UEAuthentication_Authenticate Response + SUPI + KSEAF */
    private SignalingMessage nausfAuthResp(String ueId) {
        NausfUeAuthRespPayload pl = new NausfUeAuthRespPayload();
        pl.setSupi(ueId);
        pl.setKseaf("KSEAF-HEX");

        return buildMsg(
                ueId,
                "N12",
                "UL",
                "Nausf",
                "Nausf_UEAuthentication_Authenticate Response",
                pl
        );
    }

    /** NAS SecurityModeCommand + NAS enc/int 绠楁硶 */
    private SignalingMessage nasSmc(String ueId) {
        NasSecurityModeCommandPayload pl = new NasSecurityModeCommandPayload();
        pl.setNasEncAlg("NEA2");
        pl.setNasIntAlg("NIA2");

        return buildMsg(
                ueId,
                "N2",
                "DL",
                "NAS",
                "NAS SecurityModeCommand",
                pl
        );
    }

    /** Initial Context Setup Request + KgNB */
    private SignalingMessage initialContextSetupReq(String ueId) {
        NgapInitialContextSetupReqPayload pl = new NgapInitialContextSetupReqPayload();
        pl.setKgNb("KGNB-HEX");

        return buildMsg(
                ueId,
                "N2",
                "DL",
                "NGAP",
                "Initial Context Setup Request",
                pl
        );
    }

    /** RRC SecurityModeCommand + RRC/UP enc/int 绠楁硶 */
    private SignalingMessage rrcSmc(String ueId) {
        RrcSecurityModeCommandPayload pl = new RrcSecurityModeCommandPayload();
        pl.setRrcEncAlg("RRC-NEA2");
        pl.setRrcIntAlg("RRC-NIA2");
        pl.setUpEncAlg("UP-NEA2");
        pl.setUpIntAlg("UP-NIA2");

        return buildMsg(
                ueId,
                "Uu",
                "DL",
                "RRC",
                "RRC SecurityModeCommand",
                pl
        );
    }

    /** RRCReconfiguration + DRB UP 瀹夊叏閰嶇疆 */
    private SignalingMessage rrcReconfiguration(String ueId) {
        RrcReconfigurationPayload pl = new RrcReconfigurationPayload();
        pl.setHasDrbSecurityConfig(true);
        pl.setDrbUpEncActivated(true);
        pl.setDrbUpIntActivated(true);

        return buildMsg(
                ueId,
                "Uu",
                "DL",
                "RRC",
                "RRCReconfiguration",
                pl
        );
    }

    /** 浠绘剰涓€鏉＄粨鏉?IA 鐨勪俊浠わ紝姣斿 Registration Complete */
    private SignalingMessage iaEndMsg(String ueId) {
        return buildMsg(
                ueId,
                "N2",
                "UL",
                "NAS",
                "Registration Complete",
                null
        );
    }

    /** 涓€涓畬鍏ㄦ棤鍏崇殑娑堟伅锛岀敤鏉ユ祴璇曢潪娴佺▼鍦烘櫙 */
    private SignalingMessage unrelatedMsg(String ueId) {
        return buildMsg(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRC_CONFIG_UPDATE",
                null
        );
    }

    // ====== Helper assertions and scenario utilities ======

    private void assertNewIaProcedure(MessageProcessingResult r) {
        assertNotNull(r, "result should not be null");
//        assertTrue(r.isProcedureRelated(), "should be procedure related");
//        assertTrue(r.isNewProcedure(), "should create new procedure");
        assertEquals(ProcedureTypeEnum.INITIAL_ACCESS.getCode(), r.getProcedureType());
        assertNotNull(r.getProcedureId(), "procedureId should not be null");
    }

    private void assertSameProcedure(MessageProcessingResult rPrev, MessageProcessingResult rCurr) {
        assertNotNull(rPrev.getProcedureId());
        assertNotNull(rCurr.getProcedureId());
        assertEquals(rPrev.getProcedureId(), rCurr.getProcedureId(),
                "messages should be attached to the same procedure");
    }

    private void assertNonProcedure(MessageProcessingResult r) {
        assertNotNull(r);
//        assertFalse(r.isProcedureRelated(), "should NOT be treated as procedure-driving");
    }

    // ==========================
    //      Scenario tests
    // ==========================

    @Test
    @DisplayName("瀹屾暣鐨?IA 姝ｅ父娴佺▼锛氫粠 RRCSetupComplete 鍒?RRCReconfiguration + 缁撴潫淇′护")
    void testInitialAccessHappyPath() {
        String ueId = "460011234567899";

        // 0. RRCSetupComplete + Registration request锛堥樁娈?璧峰锛?
        MessageProcessingResult r0 =
                messageApplicationService.process(rrcSetupCompleteStart(ueId));
        System.out.println("Step0 = " + r0);
        assertNewIaProcedure(r0);

        // 1. Initial UE Message锛堥樁娈?璧峰锛?
        MessageProcessingResult r1 =
                messageApplicationService.process(initialUeMessageStart(ueId));
        System.out.println("Step1 = " + r1);
        assertSameProcedure(r0, r1);

        // 2. Nausf_UEAuthentication_Authenticate Response锛堥樁娈?璧峰锛?
        MessageProcessingResult r2 =
                messageApplicationService.process(nausfAuthResp(ueId));
        System.out.println("Step2 = " + r2);
        assertSameProcedure(r0, r2);

        // 3. NAS SecurityModeCommand锛堥樁娈?璧峰锛?
        MessageProcessingResult r3 =
                messageApplicationService.process(nasSmc(ueId));
        System.out.println("Step3 = " + r3);
        assertSameProcedure(r0, r3);

        // 4. Initial Context Setup Request锛堥樁娈?璧峰锛?
        MessageProcessingResult r4 =
                messageApplicationService.process(initialContextSetupReq(ueId));
        System.out.println("Step4 = " + r4);
        assertSameProcedure(r0, r4);

        // 5. RRC SecurityModeCommand锛堥樁娈?璧峰锛?
        MessageProcessingResult r5 =
                messageApplicationService.process(rrcSmc(ueId));
        System.out.println("Step5 = " + r5);
        assertSameProcedure(r0, r5);

        // 6. RRCReconfiguration锛堥樁娈?璧峰锛?
        MessageProcessingResult r6 =
                messageApplicationService.process(rrcReconfiguration(ueId));
        System.out.println("Step6 = " + r6);
        assertSameProcedure(r0, r6);

        // 7. 浠绘剰缁撴潫淇′护锛圧egistration Complete / RRCReconfigurationComplete 绛夛級
        MessageProcessingResult r7 =
                messageApplicationService.process(iaEndMsg(ueId));
        System.out.println("Step7(end) = " + r7);
        // 杩欓噷浣犲彲浠ユ牴鎹疄鐜板垽鏂細鍙兘浠嶈繑鍥炲悓涓€涓?procedureId锛屼篃鍙兘鏍囪宸茬粡褰掓。
    }

    @Test
    @DisplayName("RRCSetupComplete 娌℃湁璐熻浇/鍏抽敭瀛楁鏃朵笉搴斿惎鍔?IA 娴佺▼")
    void testRrcSetupCompleteWithoutPayloadShouldNotStartIa() {
        String ueId = "460011234567800";

        // 娌℃湁 payload锛屾垨 payload.hasRegistrationRequest=false
        SignalingMessage msg = buildMsg(
                ueId, "Uu", "UL", "RRC",
                "RRCSetupComplete", null
        );

        MessageProcessingResult r =
                messageApplicationService.process(msg);
        System.out.println("RRCSetupComplete without payload = " + r);

        // 棰勬湡锛氳涔堣褰撲綔闈炴祦绋嬫秷鎭紝瑕佷箞鑷冲皯涓嶈兘鍒涘缓 IA 娴佺▼
        // 涓嬮潰涓よ鎸変綘鍏蜂綋瀹炵幇浜岄€変竴锛?
        // assertNonProcedure(r);
        assertNotEquals(ProcedureTypeEnum.INITIAL_ACCESS, r.getProcedureType(),
                "should NOT start IA when payload is missing");
    }

    @Test
    @DisplayName("initial ue message should ignore incomplete payload")
    void testInitialUeMessageMissingFields() {
        String ueId = "460011234567801";

        // 鍏堢敤姝ｅ父鐨?RRCSetupComplete 鍚姩 IA
        MessageProcessingResult r0 =
                messageApplicationService.process(rrcSetupCompleteStart(ueId));
        assertNewIaProcedure(r0);

        // 鏋勯€犱竴涓?payload 涓嶅畬鏁寸殑 Initial UE Message锛堜緥濡傛病鏈?NCGI锛?
        NgapInitialUeMessagePayload pl = new NgapInitialUeMessagePayload();
        pl.setHasRegistrationRequest(true);
        pl.setRanUeNgapId("RAN-UE-1");
        pl.setNcgi(null);  // 鏁呮剰缂哄け

        SignalingMessage badInitialUe = buildMsg(
                ueId, "N2", "UL", "NGAP",
                "Initial UE Message", pl
        );

        MessageProcessingResult r1 =
                messageApplicationService.process(badInitialUe);
        System.out.println("Bad Initial UE Message = " + r1);

        // 棰勬湡锛氫笉浼氭柊寤轰竴涓?IA 娴佺▼锛屼篃涓嶄細琚綋鎴愨€減hase1 璧峰鈥?
        assertSame(r0.getProcedureId(), r1.getProcedureId(),
                "should still attach to existing procedure, or be treated as aux");
        // 濡傛灉浣犲湪 hasValidPayloadForPhaseStart 閲岀洿鎺ヨ繑鍥?false锛屽垯涓嶄細灏嗛樁娈垫帹杩?
        // 杩欓噷鍙互鏍规嵁闇€瑕侊紝澧炲姞瀵?phaseIndex 鐨勬柇瑷€锛堝鏋滃澶栧彲瑙佺殑璇濓級
    }

    @Test
    @DisplayName("瀹屽叏鏃犲叧鐨勬秷鎭簲褰撹鏍囪涓洪潪娴佺▼娑堟伅")
    void testNonProcedureMsg() {
        String ueId = "460011234567900";

        MessageProcessingResult r =
                messageApplicationService.process(unrelatedMsg(ueId));
        System.out.println("Non-procedure = " + r);

        // 濡傛灉浣犵殑瀹炵幇瀵光€滃畬鍏ㄤ笉鍦?Driving/Aux 闆嗗悎鐨勬秷鎭€濊繑鍥為潪娴佺▼锛?
        // assertNonProcedure(r);
    }

    @Test
    @DisplayName("涓や釜涓嶅悓 UE 鐨?IA 娴佺▼搴斿綋浜х敓涓嶅悓鐨?procedureId")
    void testDifferentUeHaveDifferentIa() {
        String ue1 = "460011234567811";
        String ue2 = "460011234567822";

        MessageProcessingResult r1 =
                messageApplicationService.process(rrcSetupCompleteStart(ue1));
        MessageProcessingResult r2 =
                messageApplicationService.process(rrcSetupCompleteStart(ue2));

        System.out.println("UE1 IA = " + r1);
        System.out.println("UE2 IA = " + r2);

        assertNewIaProcedure(r1);
        assertNewIaProcedure(r2);
        assertNotEquals(r1.getProcedureId(), r2.getProcedureId(),
                "different UE should have different IA procedures");
    }

    @Test
    @DisplayName("same ue should not start duplicate initial access procedures")
    void testConcurrentIaStartForSameUe() {
        String ueId = "460011234567833";

        // 绗竴鏉?IA 璧峰
        MessageProcessingResult r1 =
                messageApplicationService.process(rrcSetupCompleteStart(ueId));
        // 绗簩鏉?IA 璧峰锛堟瘮濡傞噸浼?/ 閲嶅锛?
        MessageProcessingResult r2 =
                messageApplicationService.process(rrcSetupCompleteStart(ueId));

        System.out.println("IA start #1 = " + r1);
        System.out.println("IA start #2 = " + r2);

        // 杩欓噷鏍规嵁浣犵殑绛栫暐锛屽彲鑳芥槸锛?
        // - 绗竴娆?newProcedure=true锛岀浜屾浠嶇劧鎸傚湪鍚屼竴涓?procedureId 涓婏紙new=false锛?
        // 涓嬮潰鏄竴涓ず渚嬫柇瑷€锛屾寜闇€瑕佽皟鏁达細
        assertEquals(r1.getProcedureId(), r2.getProcedureId(),
                "duplicate IA start should not create multiple procedures for same UE");
    }

    private void replayMessages(List<SignalingMessage> messages) {
        for (SignalingMessage msg : messages) {
            // Replay uses the same formal ingress pipeline as production sources.
            signalingMessagePipeline.process(msg);
        }

    }

    @Test
    @DisplayName("鐢ㄨВ鏋愬悗鐨?SignalingMessage 鍒楄〃椹卞姩 IA 娴佺▼鍥炴斁")
    void testReplayParsedMessages() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        // TODO: 杩欓噷鎶婅矾寰勬敼鎴愪綘瀹為檯鐨?logic/raw json 璺緞
        String gnbPath  = "gnb_capture.json";
        String gnbPath_raw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePath_raw = "5g_srsRAN_n78_gain40_amf_raw.json";

        List<SignalingMessage> merged = parser.parseAndMergeNoPin(gnbPath, corePath,
                gnbPath_raw, corePath_raw);


        System.out.println("Merged message count = " + merged.size());

        // 閫愭潯鍠傝繘 MsgProcessing_Service
        replayMessages(merged);

        // 濡傛灉浣犳兂鍔犳柇瑷€锛屾瘮濡傝嚦灏戜骇鐢熶竴涓?IA 娴佺▼锛屽彲浠ュ湪杩欓噷鎿嶄綔锛?
        // 鐩墠 MessageProcessingResult 鏄?process 鏃惰繑鍥炵殑锛?
        // 浣犲彲浠ュ湪 replayMessages 閲屾敼鎴愭敹闆嗗埌涓€涓?List<MessageProcessingResult> 鍐嶈繑鍥烇紝
        // 鐒跺悗鍦ㄨ繖閲屽仛鍚勭 assert銆?
    }

    @Test
    void contextLoads() throws Exception {
        Path pcap = Path.of("5g_srsRAN_n78_gain40_amf.pcapng");

        Set<String> wanted = Set.of(
                "nas-5gs_raw",
                "nas-5gs",
                "nr-rrc",
                "mac-nr",
                "mac-nr_raw",
                "ngap",
                "http2",
                "json.object"
        );

        Set<String> enabledRaw = Set.of(
                "nas-5gs_raw",
                "mac-nr_raw"
        );

        // 鐩存帴璧版寮忓叆鍙ｆ湇鍔?
        PcapBatchProcessRequest request1 =
                PcapBatchProcessRequest.of(pcap, wanted, enabledRaw);
        pcapBatchProcessor.process(request1);
    }





    @Test
    void processTwoPcaps() throws Exception {
        Path pcap1 = Path.of("gnb_capture.pcap");
        Path pcap2 = Path.of("5g_srsRAN_n78_gain40_amf.pcapng");

        Set<String> wanted = Set.of(
                "nas-5gs_raw",
                "nas-5gs",
                "nr-rrc",
                "mac-nr",
                "mac-nr_raw",
                "ngap",
                "http2",
                "json.object"
        );

        Set<String> enabledRaw = Set.of(
                "nas-5gs_raw",
                "mac-nr_raw"
        );

        PcapBatchProcessRequest request1 =
                PcapBatchProcessRequest.of(pcap1, wanted, enabledRaw);
        PcapBatchProcessRequest request2 =
                PcapBatchProcessRequest.of(pcap2, wanted, enabledRaw);

        pcapBatchProcessor.process(request1);
        pcapBatchProcessor.process(request2);
    }

}
