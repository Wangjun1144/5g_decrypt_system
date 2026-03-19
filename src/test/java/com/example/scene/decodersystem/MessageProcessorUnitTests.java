package com.example.scene.decodersystem;

import com.example.procedure.context.UeContextService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.MessageClassificationService;
import com.example.procedure.processing.message.MessageDecryptStage;
import com.example.procedure.processing.message.MessageProcessingContext;
import com.example.procedure.processing.message.MessageProcessingResultFactory;
import com.example.procedure.processing.message.MessageProcessor;
import com.example.procedure.processing.message.PendingRetryService;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import com.example.procedure.processing.procedure.ProcedureProcessingStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MessageProcessor 的最小主链行为测试。
 *
 * 这组测试验证的是“主链控制语义”，不是具体验证业务规则细节。
 *
 * 当前重点验证：
 * 1. 解密 WAITING 时：
 *    - 进入 pending 队列
 *    - 不进入流程阶段
 *    - 不触发 pending 重试
 *
 * 2. 解密 OK 且回流成功时：
 *    - 会重新进入完整主链
 *    - 第二轮继续进入流程阶段并触发 pending 重试
 *
 * 3. 不需要提前结束时：
 *    - 会进入流程阶段
 *    - 会触发 pending 重试
 *
 * 注意：
 * MessageProcessor 当前不只是依赖
 * messageDecryptStage.handleEncryptedMessageIfNeeded(...) 的返回值，
 * 还依赖 context.isDecryptOk() / context.isDecryptWaiting()。
 *
 * 因此测试里 mock 解密阶段时，必须同时把 decryptResult 写回 context。
 */
class MessageProcessorUnitTests {

    @Mock
    private UeContextService ueContextService;

    @Mock
    private MessageClassificationService classificationService;

    @Mock
    private MessageDecryptStage messageDecryptStage;

    @Mock
    private ProcedureProcessingStage procedureProcessingStage;

    @Mock
    private PendingDecryptQueue pendingDecryptQueue;

    @Mock
    private PendingRetryService pendingRetryService;

    private MessageProcessor messageProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MessageProcessingResultFactory resultFactory = new MessageProcessingResultFactory();

        messageProcessor = new MessageProcessor(
                ueContextService,
                classificationService,
                messageDecryptStage,
                procedureProcessingStage,
                pendingDecryptQueue,
                resultFactory,
                pendingRetryService
        );
    }

    /**
     * 构造一条最小可用消息。
     *
     * 当前测试只关注主链编排，不关注具体协议字段完整性。
     */
    private SignalingMessage buildMessage() {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId("460011234567890");
        msg.setMsgType("Initial UE Message");
        msg.setIface("N2");
        msg.setDirection("UL");
        msg.setProtocolLayer("NGAP");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setFrameNo(1L);
        return msg;
    }

    /**
     * 让分类阶段把消息标记为 PROCEDURE_DRIVING，
     * 这样当主链继续往后走时，流程阶段就具备进入条件。
     */
    private void stubClassificationAsProcedureDriving() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setCategory(MessageCategory.PROCEDURE_DRIVING);
            return null;
        }).when(classificationService).classify(any(MessageProcessingContext.class));
    }

    /**
     * 模拟解密阶段返回 WAITING，并把结果写回 context。
     */
    private void stubDecryptWaiting(DecryptAttemptResult.WaitReason reason) {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            DecryptAttemptResult result = DecryptAttemptResult.waiting(reason);
            context.setDecryptResult(result);
            return result;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    /**
     * 模拟解密阶段第一轮返回 OK、第二轮返回 null，
     * 并在第一轮把 OK 写回 context。
     *
     * 这样可以真实触发 MessageProcessor 的“回流后递归重新进入主链”逻辑。
     */
    private void stubDecryptOkThenContinue() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            DecryptAttemptResult result = DecryptAttemptResult.ok();
            context.setDecryptResult(result);
            return result;
        }).doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setDecryptResult(null);
            return null;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    /**
     * 模拟当前消息在解密阶段无需提前结束主链。
     *
     * 注意这里同时显式把 decryptResult 清空，
     * 避免上下文状态影响后续判断。
     */
    private void stubDecryptNoEarlyExit() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setDecryptResult(null);
            return null;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    @Test
    @DisplayName("解密 WAITING 时应进入 pending 队列，并提前结束当前主链")
    void processShouldEnqueueWhenDecryptWaiting() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptWaiting(DecryptAttemptResult.WaitReason.WAIT_ALG);

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);

        MessageProcessingResult result = messageProcessor.process(msg);

        assertNotNull(result);

        verify(classificationService, times(1))
                .classify(any(MessageProcessingContext.class));

        // 只会读取一次上下文，因为 WAITING 后会提前返回。
        verify(ueContextService, times(1)).getContext(msg.getUeId());

        verify(messageDecryptStage, times(1))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
        verify(messageDecryptStage, never())
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        verify(pendingDecryptQueue, times(1))
                .enqueue(eq(msg.getUeId()), eq(msg), eq(DecryptAttemptResult.WaitReason.WAIT_ALG));

        verifyNoInteractions(procedureProcessingStage);
        verifyNoInteractions(pendingRetryService);
    }

    @Test
    @DisplayName("解密成功且回流成功时，应重新进入完整主链")
    void processShouldReenterMainChainWhenDecryptSuccessAndReentered() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptOkThenContinue();

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);
        when(messageDecryptStage.handleDecryptSuccess(any(MessageProcessingContext.class)))
                .thenReturn(true);

        MessageProcessingResult result = messageProcessor.process(msg);

        assertNotNull(result);

        // 第一轮 + 回流后的第二轮，共两次。
        verify(classificationService, times(2))
                .classify(any(MessageProcessingContext.class));

        verify(messageDecryptStage, times(2))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));

        // 解密成功回流动作只在第一轮发生一次。
        verify(messageDecryptStage, times(1))
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        // 第二轮才会继续进入流程阶段。
        verify(procedureProcessingStage, times(1))
                .process(any(MessageProcessingContext.class));

        // 第二轮结束后会重新读取上下文并触发 pending 重试。
        verify(ueContextService, times(3)).getContext(msg.getUeId());
        verify(pendingRetryService, times(1))
                .retryPendingDecrypt(eq(msg.getUeId()), eq(ueContext));

        verifyNoInteractions(pendingDecryptQueue);
    }

    @Test
    @DisplayName("无需提前结束时，应进入流程阶段并触发 pending 重试")
    void processShouldContinueToProcedureStageAndRetryPending() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptNoEarlyExit();

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);

        MessageProcessingResult result = messageProcessor.process(msg);

        assertNotNull(result);

        verify(classificationService, times(1))
                .classify(any(MessageProcessingContext.class));

        // 一次是主链开始时读取上下文，一次是流程阶段后重新读取最新上下文。
        verify(ueContextService, times(2)).getContext(msg.getUeId());

        verify(messageDecryptStage, times(1))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
        verify(messageDecryptStage, never())
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        verify(procedureProcessingStage, times(1))
                .process(any(MessageProcessingContext.class));

        verify(pendingRetryService, times(1))
                .retryPendingDecrypt(eq(msg.getUeId()), eq(ueContext));

        verifyNoInteractions(pendingDecryptQueue);
    }
}
