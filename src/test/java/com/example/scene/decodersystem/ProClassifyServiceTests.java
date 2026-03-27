package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.application.procedure.ProcedureStateApplicationService;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.recognize.ProcedureRecognitionService;
import com.example.procedure.processing.procedure.state.ActiveProceduresView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo-style tests for procedure classification flow.
 */
@SpringBootTest(classes = Application.class)
class ProClassifyServiceTests {

    @Autowired
    private ProcedureRecognitionService proClassifyService;

    @Autowired
    private ProcedureStateApplicationService procedureStateApplicationService;

    /**
     * Builds a minimal signaling message for procedure-flow demos.
     */
    private SignalingMessage buildMessage(
            String ueId,
            String iface,
            String direction,
            String protocolLayer,
            String msgType,
            long frameNo
    ) {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId(ueId);
        msg.setIface(iface);
        msg.setDirection(direction);
        msg.setProtocolLayer(protocolLayer);
        msg.setMsgType(msgType);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setFrameNo(frameNo);

        long now = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1000000);
        msg.setMsgId(msgType + "-" + now + "-" + String.format("%06d", rand));
        return msg;
    }

    @Test
    void testInitialAccessStart() {
        String ueId = "460011234567891";

        SignalingMessage msg = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRC_SETUP_REQUEST",
                1L
        );

        ProcedureMatchResult result = proClassifyService.recognize(msg);
        System.out.println("InitialAccess start result = " + result);

        ActiveProceduresView activeProcedures = procedureStateApplicationService.getActiveProcedures(ueId);
        System.out.println("Active procedures after start = " + activeProcedures);
    }

    @Test
    void testInitialAccessFollowAndEnd() throws InterruptedException {
        String ueId = "460011234567892";

        SignalingMessage msg1 = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "RRC_SETUP_REQUEST",
                1L
        );
        ProcedureMatchResult r1 = proClassifyService.recognize(msg1);
        System.out.println("Step1 (start) result = " + r1);
        String procedureId = (String) r1.getProcedureId();

        Thread.sleep(10L);

        SignalingMessage msg2 = buildMessage(
                ueId,
                "Uu",
                "DL",
                "RRC",
                "RRC_SETUP",
                2L
        );
        ProcedureMatchResult r2 = proClassifyService.recognize(msg2);
        System.out.println("Step2 (follow) result = " + r2);

        SignalingMessage msg3 = buildMessage(
                ueId,
                "N2",
                "DL",
                "NGAP",
                "NGAP_UE_CONTEXT_RELEASE_COMMAND",
                3L
        );
        ProcedureMatchResult r3 = proClassifyService.recognize(msg3);
        System.out.println("Step3 (end) result = " + r3);

        ActiveProceduresView activeProcedures = procedureStateApplicationService.getActiveProcedures(ueId);
        System.out.println("Active procedures after end = " + activeProcedures);
        System.out.println("Expect count = 0, actual = " + activeProcedures.getCount()
                + ", original procedureId = " + procedureId);
    }

    @Test
    void testUnknownProcedure() {
        String ueId = "460011234567893";

        SignalingMessage msg = buildMessage(
                ueId,
                "Uu",
                "UL",
                "RRC",
                "SOME_UNKNOWN_MSG",
                100L
        );

        ProcedureMatchResult result = proClassifyService.recognize(msg);
        System.out.println("Unknown procedure result = " + result);

        ActiveProceduresView activeProcedures = procedureStateApplicationService.getActiveProcedures(ueId);
        System.out.println("Active procedures (UNKNOWN) = " + activeProcedures);
    }
}
