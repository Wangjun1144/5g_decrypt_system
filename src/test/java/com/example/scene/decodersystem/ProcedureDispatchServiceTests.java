package com.example.scene.decodersystem;

import com.example.procedure.processing.context.UeContextService;
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
 * ProcedureDispatchService 鐨勬渶灏忚涓烘祴璇曘€?
 *
 * 褰撳墠鍏虫敞鐐癸細
 * 1. IA + PROCEDURE_DRIVING 鏃讹紝搴旇Е鍙?UEContext 鏇存柊
 * 2. 闈?IA 娴佺▼鏃讹紝涓嶅簲瑙﹀彂 IA 涓婁笅鏂囨洿鏂?
 * 3. 闈炴祦绋嬮┍鍔ㄦ秷鎭椂锛屼笉搴旇Е鍙?IA 涓婁笅鏂囨洿鏂?
 *
 * 杩欑粍娴嬭瘯鐨勭洰鏍囦笉鏄獙璇佸畬鏁翠笟鍔¤鍒欙紝
 * 鑰屾槸楠岃瘉鈥滄柊鐨勬祦绋嬪垎鍙戣竟鐣屸€濆凡缁忕湡姝ｆ壙鎺ユ棫琛屼负銆?
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
//    @DisplayName("IA 娴佺▼椹卞姩娑堟伅搴旇Е鍙?UE 涓婁笅鏂囨洿鏂?)
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
//    @DisplayName("闈?IA 娴佺▼涓嶅簲瑙﹀彂 Initial Access 涓婁笅鏂囨洿鏂?)
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
//    @DisplayName("闈炴祦绋嬮┍鍔ㄦ秷鎭笉搴旇Е鍙?Initial Access 涓婁笅鏂囨洿鏂?)
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
