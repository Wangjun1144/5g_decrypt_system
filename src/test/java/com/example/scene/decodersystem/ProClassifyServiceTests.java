package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.service.ProClassify_Service;
import com.example.procedure.service.ProManager_Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用于测试 ProClassify_Service 的简单 DEMO 测试类
 */
@SpringBootTest(classes = Application.class)
class ProClassifyServiceTests {

    @Autowired
    private ProClassify_Service proClassifyService;

    @Autowired
    private ProManager_Service proManagerService;

    /**
     * 构造一条简单的信令消息
     */
    private SignalingMessage buildMessage(String ueId,
                                          String iface,
                                          String direction,
                                          String protocolLayer,
                                          String msgType,
                                          long frameNo) {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId(ueId);
        msg.setIface(iface);
        msg.setDirection(direction);
        msg.setProtocolLayer(protocolLayer);
        msg.setMsgType(msgType);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setFrameNo(frameNo);

        long now = System.currentTimeMillis(); // 毫秒级时间戳
        int rand = ThreadLocalRandom.current().nextInt(1000000); // 0 ~ 999999 之间的随机数

        String msgId = msgType + "-" + now + "-" + String.format("%06d", rand);
        msg.setMsgId(msgId);

        return msg;
    }

    /**
     * 1️⃣ 测试：初始接入起始消息能否正确创建一个新流程
     * 规则里 INITIAL_ACCESS 的 startMessages = { "RRC_SETUP_REQUEST" }
     */
    @Test
    void testInitialAccessStart() {
        String ueId = "460011234567891";

        // RRC_SETUP_REQUEST 是 INITIAL_ACCESS 的起始消息
        SignalingMessage msg = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRC_SETUP_REQUEST",
                1L
        );

        ProcedureMatchResult result = proClassifyService.handleMessage(msg);
        System.out.println("InitialAccess start result = " + result);

        // 顺便看看当前 UE 的活跃流程
        Map<String, Object> activeProcedures = proManagerService.get_ActProcedures(ueId);
        System.out.println("Active procedures after start = " + activeProcedures);
    }

    /**
     * 2️⃣ 测试：后续消息是否会挂到同一个 INITIAL_ACCESS 流程上，并在结束消息时结束流程
     */
    @Test
    void testInitialAccessFollowAndEnd() throws InterruptedException {
        String ueId = "460011234567892";

        // 第 1 条：起始消息，创建 IA 流程
        SignalingMessage msg1 = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRC_SETUP_REQUEST",
                1L
        );
        ProcedureMatchResult r1 = proClassifyService.handleMessage(msg1);
        System.out.println("Step1 (start) result = " + r1);
        String procedureId = (String) r1.getProcedureId();

        // 为了时间差明显一点，随便 sleep 一下
        Thread.sleep(10L);

        // 第 2 条：RRC_SETUP，规则 allowedNext 里允许作为 RRC_SETUP_REQUEST 的下一条
        SignalingMessage msg2 = buildMessage(
                ueId,
                "Uu",
                "DL",
                "RRC",
                "RRC_SETUP",
                2L
        );
        ProcedureMatchResult r2 = proClassifyService.handleMessage(msg2);
        System.out.println("Step2 (follow) result = " + r2);

        // 第 3 条：NGAP_UE_CONTEXT_RELEASE_COMMAND，被标记为 INITIAL_ACCESS 的结束消息
        SignalingMessage msg3 = buildMessage(
                ueId,
                "N2",
                "DL",
                "NGAP",
                "NGAP_UE_CONTEXT_RELEASE_COMMAND",
                3L
        );
        ProcedureMatchResult r3 = proClassifyService.handleMessage(msg3);
        System.out.println("Step3 (end) result = " + r3);

        // 再看看当前 UE 的活跃流程，理论上应该被 end_Procedure 清理掉
        Map<String, Object> activeProcedures = proManagerService.get_ActProcedures(ueId);
        System.out.println("Active procedures after end = " + activeProcedures);
        System.out.println("Expect count = 0, actual = " + activeProcedures.get("count")
                + ", original procedureId = " + procedureId);
    }

    /**
     * 3️⃣ 测试：一条完全不在任何规则里的消息，会被归入 UNKNOWN 流程
     */
    @Test
    void testUnknownProcedure() {
        String ueId = "460011234567893";

        // msgType 不在任何 startMessages 里
        SignalingMessage msg = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "SOME_UNKNOWN_MSG",
                100L
        );

        ProcedureMatchResult result = proClassifyService.handleMessage(msg);
        System.out.println("Unknown procedure result = " + result);

        Map<String, Object> activeProcedures = proManagerService.get_ActProcedures(ueId);
        System.out.println("Active procedures (UNKNOWN) = " + activeProcedures);
    }
}
