package com.example.scene.decodersystem;

import com.example.procedure.context.UeContextService;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.dispatch.ProcedureDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

/**
 * ProcedureDispatchService 的最小行为测试。
 *
 * 当前关注点：
 * 1. IA + PROCEDURE_DRIVING 时，应触发 UEContext 更新
 * 2. 非 IA 流程时，不应触发 IA 上下文更新
 * 3. 非流程驱动消息时，不应触发 IA 上下文更新
 *
 * 这组测试的目标不是验证完整业务规则，
 * 而是验证“新的流程分发边界”已经真正承接旧行为。
 */
class ProcedureDispatchServiceTests {

    @Mock
    private UeContextService ueContextService;

    private ProcedureDispatchService procedureDispatchService;

//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        procedureDispatchService = new ProcedureDispatchService(ueContextService);
//    }
//
//    private SignalingMessage buildMessage() {
//        SignalingMessage msg = new SignalingMessage();
//        msg.setUeId("460011234567890");
//        msg.setMsgType("Initial UE Message");
//        msg.setIface("N2");
//        msg.setDirection("UL");
//        msg.setProtocolLayer("NGAP");
//        msg.setTimestamp(System.currentTimeMillis());
//        msg.setFrameNo(1L);
//        return msg;
//    }

//    @Test
//    @DisplayName("IA 流程驱动消息应触发 UE 上下文更新")
//    void dispatchShouldUpdateContextForInitialAccessProcedureDrivingMessage() {
//        SignalingMessage msg = buildMessage();
//
//        procedureDispatchService.dispatch(
//                msg,
//                MessageCategory.PROCEDURE_DRIVING,
//                "procedure-1",
//                "IA"
//        );
//
//        verify(ueContextService, times(1))
//                .updateOnInitialAccess(msg, "procedure-1");
//    }

//    @Test
//    @DisplayName("非 IA 流程不应触发 Initial Access 上下文更新")
//    void dispatchShouldNotUpdateContextForNonInitialAccessProcedure() {
//        SignalingMessage msg = buildMessage();
//
//        procedureDispatchService.dispatch(
//                msg,
//                MessageCategory.PROCEDURE_DRIVING,
//                "procedure-2",
//                "XN_HO"
//        );
//
//        verifyNoInteractions(ueContextService);
//    }
//
//    @Test
//    @DisplayName("非流程驱动消息不应触发 Initial Access 上下文更新")
//    void dispatchShouldNotUpdateContextForNonProcedureDrivingMessage() {
//        SignalingMessage msg = buildMessage();
//
//        procedureDispatchService.dispatch(
//                msg,
//                MessageCategory.NON_PROCEDURE,
//                "procedure-3",
//                "IA"
//        );
//
//        verifyNoInteractions(ueContextService);
//    }
}
