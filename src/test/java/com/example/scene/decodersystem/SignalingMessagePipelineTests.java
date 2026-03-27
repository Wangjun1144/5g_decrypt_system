package com.example.scene.decodersystem;

import com.example.procedure.application.message.CoordinatingSignalingMessagePipeline;
import com.example.procedure.application.message.MessageApplicationOutcome;
import com.example.procedure.application.message.MessageApplicationService;
import com.example.procedure.application.message.SignalingMessageIngressRequest;
import com.example.procedure.application.binding.BindingApplicationOutcome;
import com.example.procedure.application.binding.BindingApplicationService;
import com.example.procedure.application.binding.BindingProcessRequest;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Minimal behavior tests for the single-message pipeline entry.
 *
 * These tests verify orchestration semantics only:
 * 1. Every message enters the binding stage first.
 * 2. Messages released by binding are forwarded to the main message coordinator.
 * 3. Buffered messages stop at binding and do not enter the main chain.
 */
class SignalingMessagePipelineTests {

    @Mock
    private BindingApplicationService bindingApplicationService;

    @Mock
    private MessageApplicationService messageApplicationService;

    private CoordinatingSignalingMessagePipeline pipeline;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pipeline = new CoordinatingSignalingMessagePipeline(bindingApplicationService, messageApplicationService);
    }

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
    @DisplayName("Pipeline enters binding stage first")
    void processShouldEnterBindingStageFirst() {
        SignalingMessage msg = buildMinimalMessage();

        pipeline.process(msg);

        verify(bindingApplicationService, times(1))
                .processDetailed(any(BindingProcessRequest.class));
        verifyNoInteractions(messageApplicationService);
    }

    @Test
    @DisplayName("Binding release forwards message into main coordinator")
    void processShouldEnterMainProcessorWhenBindingStageReleasesMessage() {
        SignalingMessage msg = buildMinimalMessage();

        when(bindingApplicationService.processDetailed(any(BindingProcessRequest.class)))
                .thenReturn(BindingApplicationOutcome.of(
                        BindingProcessRequest.fromPipelineRequest(SignalingMessageIngressRequest.of(msg)),
                        BindingResolutionResult.ready(msg, java.util.List.of())
                ));
        when(messageApplicationService.processDetailed(any(SignalingMessageIngressRequest.class)))
                .thenAnswer(invocation -> {
                    SignalingMessageIngressRequest request = invocation.getArgument(0);
                    return MessageApplicationOutcome.of(
                            MessageProcessingRequest.fromIngressRequest(request),
                            null
                    );
                });

        pipeline.process(msg);

        verify(bindingApplicationService, times(1))
                .processDetailed(any(BindingProcessRequest.class));
        verify(messageApplicationService, times(1)).processDetailed(any(SignalingMessageIngressRequest.class));
    }

    @Test
    @DisplayName("Binding buffer does not enter main coordinator")
    void processShouldNotEnterMainProcessorWhenBindingStageBuffersMessage() {
        SignalingMessage msg = buildMinimalMessage();

        when(bindingApplicationService.processDetailed(any(BindingProcessRequest.class)))
                .thenReturn(BindingApplicationOutcome.of(
                        BindingProcessRequest.fromPipelineRequest(SignalingMessageIngressRequest.of(msg)),
                        BindingResolutionResult.buffered()
                ));

        pipeline.process(msg);

        verify(bindingApplicationService, times(1))
                .processDetailed(any(BindingProcessRequest.class));
        verifyNoInteractions(messageApplicationService);
    }

    @Test
    @DisplayName("Binding release forwards all emitted messages")
    void processShouldForwardAllMessagesReleasedByBindingStage() {
        SignalingMessage currentMsg = buildMinimalMessage();

        SignalingMessage releasedPending1 = buildMinimalMessage();
        releasedPending1.setMsgType("RRCSetupComplete");
        releasedPending1.setFrameNo(2L);

        SignalingMessage releasedPending2 = buildMinimalMessage();
        releasedPending2.setMsgType("NAS SecurityModeCommand");
        releasedPending2.setFrameNo(3L);

        when(bindingApplicationService.processDetailed(any(BindingProcessRequest.class)))
                .thenReturn(BindingApplicationOutcome.of(
                        BindingProcessRequest.fromPipelineRequest(SignalingMessageIngressRequest.of(currentMsg)),
                        BindingResolutionResult.ready(currentMsg, java.util.List.of(releasedPending1, releasedPending2))
                ));
        when(messageApplicationService.processDetailed(any(SignalingMessageIngressRequest.class)))
                .thenAnswer(invocation -> {
                    SignalingMessageIngressRequest request = invocation.getArgument(0);
                    return MessageApplicationOutcome.of(
                            MessageProcessingRequest.fromIngressRequest(request),
                            null
                    );
                });

        pipeline.process(currentMsg);

        verify(bindingApplicationService, times(1))
                .processDetailed(any(BindingProcessRequest.class));

        verify(messageApplicationService, times(3)).processDetailed(any(SignalingMessageIngressRequest.class));
    }
}
