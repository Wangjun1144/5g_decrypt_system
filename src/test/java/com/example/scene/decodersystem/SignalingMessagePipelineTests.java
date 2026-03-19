package com.example.scene.decodersystem;

import com.example.procedure.application.message.DefaultSignalingMessagePipeline;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.domain.binding.UeBindingService;
import com.example.procedure.processing.message.MessageProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/**
 * SignalingMessagePipeline 的最小链路测试。
 *
 * 这组测试对应第 16 小步引入的“单消息统一入口”：
 * - pcap 批处理入口未来只负责把消息解析出来
 * - 单条消息真正进入主链的逻辑，则统一走 SignalingMessagePipeline
 *
 * 因此这里的测试目标不是验证完整业务规则，
 * 而是验证“单消息入口”的核心编排语义：
 *
 * 1. pipeline.process(msg) 会先进入绑定阶段
 * 2. 如果绑定阶段放行消息，则消息会进入 MessageProcessor
 * 3. 如果绑定阶段缓冲消息，则不会进入 MessageProcessor
 *
 * 为什么这一步很重要：
 * - 它给后续小步重构提供了一个很稳定的最小验证点
 * - 未来无论你接 pcap 批处理、Kafka、在线流式入口，
 *   只要都复用 SignalingMessagePipeline，这组测试就持续有价值
 *
 * 当前测试方式：
 * - 不启动 Spring 容器
 * - 直接 new DefaultSignalingMessagePipeline
 * - 用 mock 验证入口编排行为
 *
 * 这样做的原因：
 * - 测试更轻
 * - 不依赖 Redis / tshark / JNI / 解密服务
 * - 更适合做“最小链路行为验证”
 */
class SignalingMessagePipelineTests {

    @Mock
    private UeBindingService ueBindingService;

    @Mock
    private MessageProcessor messageProcessor;

    private DefaultSignalingMessagePipeline pipeline;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pipeline = new DefaultSignalingMessagePipeline(ueBindingService, messageProcessor);
    }

    /**
     * 构造一条最小可用的 SignalingMessage。
     *
     * 这里故意只设置非常少的字段，
     * 因为这个测试的重点不是验证具体业务内容，
     * 而是验证“单消息入口”的编排顺序。
     */
    private SignalingMessage buildMinimalMessage() {
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

    @Test
    @DisplayName("单消息入口会先进入绑定阶段")
    void processShouldEnterBindingStageFirst() {
        SignalingMessage msg = buildMinimalMessage();

        pipeline.process(msg);

        verify(ueBindingService, times(1))
                .handle(eq(msg), ArgumentMatchers.any());
        verifyNoInteractions(messageProcessor);
    }

    @Test
    @DisplayName("绑定阶段放行当前消息后，消息会进入主处理器")
    @SuppressWarnings("unchecked")
    void processShouldEnterMainProcessorWhenBindingStageReleasesMessage() {
        SignalingMessage msg = buildMinimalMessage();

        doAnswer(invocation -> {
            SignalingMessage incoming = invocation.getArgument(0);
            Consumer<SignalingMessage> downstream = invocation.getArgument(1);
            downstream.accept(incoming);
            return null;
        }).when(ueBindingService).handle(eq(msg), ArgumentMatchers.any());

        pipeline.process(msg);

        verify(ueBindingService, times(1))
                .handle(eq(msg), ArgumentMatchers.any());
        verify(messageProcessor, times(1)).process(msg);
    }

    @Test
    @DisplayName("绑定阶段如果缓冲消息，则不会进入主处理器")
    void processShouldNotEnterMainProcessorWhenBindingStageBuffersMessage() {
        SignalingMessage msg = buildMinimalMessage();

        // 不调用 downstream.accept(...)，模拟绑定阶段把消息缓冲起来。
        doNothing().when(ueBindingService).handle(eq(msg), ArgumentMatchers.any());

        pipeline.process(msg);

        verify(ueBindingService, times(1))
                .handle(eq(msg), ArgumentMatchers.any());
        verifyNoInteractions(messageProcessor);
    }

    @Test
    @DisplayName("绑定阶段释放多条消息时，主处理器会按下游输出次数被调用")
    @SuppressWarnings("unchecked")
    void processShouldForwardAllMessagesReleasedByBindingStage() {
        SignalingMessage currentMsg = buildMinimalMessage();

        SignalingMessage releasedPending1 = buildMinimalMessage();
        releasedPending1.setMsgType("RRCSetupComplete");
        releasedPending1.setFrameNo(2L);

        SignalingMessage releasedPending2 = buildMinimalMessage();
        releasedPending2.setMsgType("NAS SecurityModeCommand");
        releasedPending2.setFrameNo(3L);

        doAnswer(invocation -> {
            Consumer<SignalingMessage> downstream = invocation.getArgument(1);

            // 模拟绑定阶段先释放历史 pending，再释放当前消息。
            downstream.accept(releasedPending1);
            downstream.accept(releasedPending2);
            downstream.accept(currentMsg);
            return null;
        }).when(ueBindingService).handle(eq(currentMsg), ArgumentMatchers.any());

        pipeline.process(currentMsg);

        verify(ueBindingService, times(1))
                .handle(eq(currentMsg), ArgumentMatchers.any());

        verify(messageProcessor, times(1)).process(releasedPending1);
        verify(messageProcessor, times(1)).process(releasedPending2);
        verify(messageProcessor, times(1)).process(currentMsg);
    }
}
