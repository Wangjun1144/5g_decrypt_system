package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.*;
import com.example.procedure.rule.UeIdBinder;
import com.example.procedure.initial_acess.*;
import com.example.procedure.parser.TsharkJsonMessageParser;
import com.example.procedure.service.MsgProcessing_Service;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.util.SignalingMessagePrinter;
import com.example.procedure.wireshark.TsharkRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
class MessageProcessingServiceTests {

    @Autowired
    private MsgProcessing_Service messageProcessingService;
    @Autowired
    private UeIdBinder ueIdBinder;

    // ====== 通用构造器 ======
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

    // ====== 针对 IA 关键消息的便捷构造 ======

    /** RRCSetupComplete + Registration request + IMSI + C-RNTI */
    private SignalingMessage rrcSetupCompleteStart(String ueId) {
        RrcSetupCompletePayload pl = new RrcSetupCompletePayload();
        pl.setHasRegistrationRequest(true);
        pl.setImsi(ueId);          // 测试里假定 ueId = IMSI
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

    /** NAS SecurityModeCommand + NAS enc/int 算法 */
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

    /** RRC SecurityModeCommand + RRC/UP enc/int 算法 */
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

    /** RRCReconfiguration + DRB UP 安全配置 */
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

    /** 任意一条结束 IA 的信令，比如 Registration Complete */
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

    /** 一个完全无关的消息，用来测试非流程场景 */
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

    // ====== 一些辅助断言方法（根据你的 MessageProcessingResult 调整 getter 名称） ======

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
    //      测试用例开始
    // ==========================

    @Test
    @DisplayName("完整的 IA 正常流程：从 RRCSetupComplete 到 RRCReconfiguration + 结束信令")
    void testInitialAccessHappyPath() {
        String ueId = "460011234567899";

        // 0. RRCSetupComplete + Registration request（阶段0起始）
        MessageProcessingResult r0 =
                messageProcessingService.process(rrcSetupCompleteStart(ueId));
        System.out.println("Step0 = " + r0);
        assertNewIaProcedure(r0);

        // 1. Initial UE Message（阶段1起始）
        MessageProcessingResult r1 =
                messageProcessingService.process(initialUeMessageStart(ueId));
        System.out.println("Step1 = " + r1);
        assertSameProcedure(r0, r1);

        // 2. Nausf_UEAuthentication_Authenticate Response（阶段2起始）
        MessageProcessingResult r2 =
                messageProcessingService.process(nausfAuthResp(ueId));
        System.out.println("Step2 = " + r2);
        assertSameProcedure(r0, r2);

        // 3. NAS SecurityModeCommand（阶段3起始）
        MessageProcessingResult r3 =
                messageProcessingService.process(nasSmc(ueId));
        System.out.println("Step3 = " + r3);
        assertSameProcedure(r0, r3);

        // 4. Initial Context Setup Request（阶段4起始）
        MessageProcessingResult r4 =
                messageProcessingService.process(initialContextSetupReq(ueId));
        System.out.println("Step4 = " + r4);
        assertSameProcedure(r0, r4);

        // 5. RRC SecurityModeCommand（阶段5起始）
        MessageProcessingResult r5 =
                messageProcessingService.process(rrcSmc(ueId));
        System.out.println("Step5 = " + r5);
        assertSameProcedure(r0, r5);

        // 6. RRCReconfiguration（阶段6起始）
        MessageProcessingResult r6 =
                messageProcessingService.process(rrcReconfiguration(ueId));
        System.out.println("Step6 = " + r6);
        assertSameProcedure(r0, r6);

        // 7. 任意结束信令（Registration Complete / RRCReconfigurationComplete 等）
        MessageProcessingResult r7 =
                messageProcessingService.process(iaEndMsg(ueId));
        System.out.println("Step7(end) = " + r7);
        // 这里你可以根据实现判断：可能仍返回同一个 procedureId，也可能标记已经归档
    }

    @Test
    @DisplayName("RRCSetupComplete 没有负载/关键字段时不应启动 IA 流程")
    void testRrcSetupCompleteWithoutPayloadShouldNotStartIa() {
        String ueId = "460011234567800";

        // 没有 payload，或 payload.hasRegistrationRequest=false
        SignalingMessage msg = buildMsg(
                ueId, "Uu", "UL", "RRC",
                "RRCSetupComplete", null
        );

        MessageProcessingResult r =
                messageProcessingService.process(msg);
        System.out.println("RRCSetupComplete without payload = " + r);

        // 预期：要么被当作非流程消息，要么至少不能创建 IA 流程
        // 下面两行按你具体实现二选一：
        // assertNonProcedure(r);
        assertNotEquals(ProcedureTypeEnum.INITIAL_ACCESS, r.getProcedureType(),
                "should NOT start IA when payload is missing");
    }

    @Test
    @DisplayName("Initial UE Message 缺少关键字段时不能推动阶段前进")
    void testInitialUeMessageMissingFields() {
        String ueId = "460011234567801";

        // 先用正常的 RRCSetupComplete 启动 IA
        MessageProcessingResult r0 =
                messageProcessingService.process(rrcSetupCompleteStart(ueId));
        assertNewIaProcedure(r0);

        // 构造一个 payload 不完整的 Initial UE Message（例如没有 NCGI）
        NgapInitialUeMessagePayload pl = new NgapInitialUeMessagePayload();
        pl.setHasRegistrationRequest(true);
        pl.setRanUeNgapId("RAN-UE-1");
        pl.setNcgi(null);  // 故意缺失

        SignalingMessage badInitialUe = buildMsg(
                ueId, "N2", "UL", "NGAP",
                "Initial UE Message", pl
        );

        MessageProcessingResult r1 =
                messageProcessingService.process(badInitialUe);
        System.out.println("Bad Initial UE Message = " + r1);

        // 预期：不会新建一个 IA 流程，也不会被当成“phase1 起始”
        assertSame(r0.getProcedureId(), r1.getProcedureId(),
                "should still attach to existing procedure, or be treated as aux");
        // 如果你在 hasValidPayloadForPhaseStart 里直接返回 false，则不会将阶段推进
        // 这里可以根据需要，增加对 phaseIndex 的断言（如果对外可见的话）
    }

    @Test
    @DisplayName("完全无关的消息应当被标记为非流程消息")
    void testNonProcedureMsg() {
        String ueId = "460011234567900";

        MessageProcessingResult r =
                messageProcessingService.process(unrelatedMsg(ueId));
        System.out.println("Non-procedure = " + r);

        // 如果你的实现对“完全不在 Driving/Aux 集合的消息”返回非流程：
        // assertNonProcedure(r);
    }

    @Test
    @DisplayName("两个不同 UE 的 IA 流程应当产生不同的 procedureId")
    void testDifferentUeHaveDifferentIa() {
        String ue1 = "460011234567811";
        String ue2 = "460011234567822";

        MessageProcessingResult r1 =
                messageProcessingService.process(rrcSetupCompleteStart(ue1));
        MessageProcessingResult r2 =
                messageProcessingService.process(rrcSetupCompleteStart(ue2));

        System.out.println("UE1 IA = " + r1);
        System.out.println("UE2 IA = " + r2);

        assertNewIaProcedure(r1);
        assertNewIaProcedure(r2);
        assertNotEquals(r1.getProcedureId(), r2.getProcedureId(),
                "different UE should have different IA procedures");
    }

    @Test
    @DisplayName("同一 UE 出现两条并发 IA 起始信令，只应保留一个主流程（视你的策略而定）")
    void testConcurrentIaStartForSameUe() {
        String ueId = "460011234567833";

        // 第一条 IA 起始
        MessageProcessingResult r1 =
                messageProcessingService.process(rrcSetupCompleteStart(ueId));
        // 第二条 IA 起始（比如重传 / 重复）
        MessageProcessingResult r2 =
                messageProcessingService.process(rrcSetupCompleteStart(ueId));

        System.out.println("IA start #1 = " + r1);
        System.out.println("IA start #2 = " + r2);

        // 这里根据你的策略，可能是：
        // - 第一次 newProcedure=true，第二次仍然挂在同一个 procedureId 上（new=false）
        // 下面是一个示例断言，按需要调整：
        assertEquals(r1.getProcedureId(), r2.getProcedureId(),
                "duplicate IA start should not create multiple procedures for same UE");
    }

    private void replayMessages(List<SignalingMessage> messages) {
        int idx = 0;
        for (SignalingMessage msg : messages) {
            idx++;

            ueIdBinder.handle(msg, m -> {
                MessageProcessingResult result = messageProcessingService.process(m);

                SignalingMessagePrinter.printAndWriteToFile(
                        m,
                        Paths.get("logs/signaling_dump.log"),
                        true
                );
            });
        }

    }

    @Test
    @DisplayName("用解析后的 SignalingMessage 列表驱动 IA 流程回放")
    void testReplayParsedMessages() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        // TODO: 这里把路径改成你实际的 logic/raw json 路径
        String gnbPath  = "gnb_capture.json";
        String gnbPath_raw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePath_raw = "5g_srsRAN_n78_gain40_amf_raw.json";

        List<SignalingMessage> merged = parser.parseAndMergeNoPin(gnbPath, corePath,
                gnbPath_raw, corePath_raw);


        System.out.println("Merged message count = " + merged.size());

        // 逐条喂进 MsgProcessing_Service
        replayMessages(merged);

        // 如果你想加断言，比如至少产生一个 IA 流程，可以在这里操作：
        // 目前 MessageProcessingResult 是 process 时返回的，
        // 你可以在 replayMessages 里改成收集到一个 List<MessageProcessingResult> 再返回，
        // 然后在这里做各种 assert。
    }

    @Autowired
    private TsharkRunner tsharkRunner;

    @Test
    void contextLoads() throws Exception{
        String tsharkJson =
                """
                [
                  {
                    "_index": "x",
                    "_type": "y",
                    "_source": {
                      "layers": {
                        "frame": {
                          "frame.number": "10",
                          "frame.time_epoch": "1700000000.123",
                          "frame.protocols": "eth:ip:sctp:nas-5gs:nr-rrc"
                        },
                        "nas-5gs_raw": ["AA11"],
                        "nas-5gs": {"nas.msg": "first"},
                        "nas-5gs_raw": ["BB22"],
                        "nas-5gs": {"nas.msg": "second"},
                        "nr-rrc_raw": ["CC33"],
                        "nr-rrc": {"rrc.msg": "hello"}
                      }
                    }
                  }
                ]
                """;
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

        // 你希望启用并严格配对抓取的 raw layer（*_raw 必须紧挨着逻辑层才会被消费）
        Set<String> enabledRaw = Set.of(
                "nas-5gs_raw",
                "mac-nr_raw"
        );

        tsharkRunner.decodeToJsonStream(pcap, in -> {
            try {
                ChainsInspectConsumer consumer = new ChainsInspectConsumer(this::processOne);
                LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void processOne(SignalingMessage msg) {
        ueIdBinder.handle(msg, m -> {
            MessageProcessingResult result = messageProcessingService.process(m);
            SignalingMessagePrinter.printAndWriteToFile(
                    m, Paths.get("logs/signaling_dump.log"), true
            );
        });
    }

    private void processPcap(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        ChainsInspectConsumer consumer = new ChainsInspectConsumer(this::processOne);

        tsharkRunner.decodeToJsonStream(pcap, in -> {
            try {
                LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void processTwoPcaps() throws Exception {
        Path pcap1 = Path.of("gnb_capture.pcap");
        Path pcap2 = Path.of("5g_srsRAN_n78_gain40_amf.pcapng");

        Set<String> wanted = Set.of("nas-5gs_raw","nas-5gs","nr-rrc","mac-nr","mac-nr_raw","ngap","http2","json.object");
        Set<String> enabledRaw = Set.of("nas-5gs_raw","mac-nr_raw");

        processPcap(pcap1, wanted, enabledRaw);
        processPcap(pcap2, wanted, enabledRaw);
    }




}
