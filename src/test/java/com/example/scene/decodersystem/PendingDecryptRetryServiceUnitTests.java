package com.example.scene.decodersystem;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.decrypt.MessageDecryptCoordinator;
import com.example.procedure.processing.message.retry.PendingDecryptBatchLoader;
import com.example.procedure.processing.message.retry.PendingDecryptBatchReporter;
import com.example.procedure.processing.message.retry.PendingDecryptItemEventPublisher;
import com.example.procedure.processing.message.retry.PendingDecryptItemRetryExecutor;
import com.example.procedure.processing.message.retry.PendingDecryptReentryHandler;
import com.example.procedure.processing.message.retry.PendingDecryptRetryIdentityFactory;
import com.example.procedure.processing.message.retry.PendingDecryptRetryPolicy;
import com.example.procedure.processing.message.retry.PendingDecryptRetryService;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.processing.pending.event.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.queue.PendingDecryptItem;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingDecryptRetryServiceUnitTests {

    @Mock
    private PendingDecryptQueue pendingDecryptQueue;

    @Mock
    private MessageDecryptCoordinator decryptCoordinator;

    @Mock
    private PendingDecryptEventPublisher pendingDecryptEventPublisher;

    @Mock
    private PendingDecryptReentryHandler reentryHandler;

    private PendingDecryptBatchLoader batchLoader;
    private PendingDecryptBatchReporter batchReporter;
    private PendingDecryptItemRetryExecutor itemRetryExecutor;
    private PendingDecryptRetryService retryService;
    private PendingDecryptRetryPolicy retryPolicy;
    private PendingDecryptRetryIdentityFactory retryIdentityFactory;
    private PendingDecryptItemEventPublisher itemEventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchLoader = new PendingDecryptBatchLoader(pendingDecryptQueue);
        batchReporter = new PendingDecryptBatchReporter(
                pendingDecryptQueue,
                pendingDecryptEventPublisher
        );
        retryPolicy = new PendingDecryptRetryPolicy();
        retryIdentityFactory = new PendingDecryptRetryIdentityFactory();
        itemEventPublisher = new PendingDecryptItemEventPublisher(
                pendingDecryptQueue,
                pendingDecryptEventPublisher,
                retryIdentityFactory
        );
        itemRetryExecutor = new PendingDecryptItemRetryExecutor(
                pendingDecryptQueue,
                decryptCoordinator,
                retryPolicy,
                retryIdentityFactory,
                itemEventPublisher
        );
        retryService = new PendingDecryptRetryService(
                batchLoader,
                batchReporter,
                itemRetryExecutor
        );
    }

    @Test
    @DisplayName("鍏峰鍙敤瀵嗛挜涓旈噸璇曟垚鍔熸椂锛屽簲鍥炴祦涓婚摼")
    void retryPendingDecryptShouldReenterMainChainWhenDecryptSucceeds() {
        SignalingMessage message = buildMessage();
        PendingDecryptItem item = PendingDecryptItem.of(
                message.getUeId(),
                message,
                DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS
        );
        UEContext context = buildNasReadyContext();

        when(pendingDecryptQueue.pollBatch(message.getUeId(), 200)).thenReturn(List.of(item));
        when(decryptCoordinator.tryDecryptByType(message, "NAS", context)).thenReturn(DecryptAttemptResult.ok());
        when(pendingDecryptQueue.size(message.getUeId())).thenReturn(1, 0);

        retryService.retryPendingDecrypt(message.getUeId(), context, reentryHandler);

        verify(decryptCoordinator, times(1)).tryDecryptByType(message, "NAS", context);
        verify(decryptCoordinator, times(1)).handleDecryptSuccess(any());

        ArgumentCaptor<MessageProcessingRequest> requestCaptor =
                ArgumentCaptor.forClass(MessageProcessingRequest.class);
        verify(reentryHandler, times(1)).reenter(requestCaptor.capture());

        MessageProcessingRequest request = requestCaptor.getValue();
        assertNotNull(request);
        assertEquals(message, request.getMessage());
        assertTrue(request.isReentry());
        assertEquals("pending-retry:" + message.getMsgId(), request.getSourceName());
        assertEquals("pending-retry-" + message.getMsgId(), request.getCorrelationId());
    }

    @Test
    @DisplayName("retry should skip queue when no key is ready")
    void retryPendingDecryptShouldSkipQueueWhenNoKeyIsReady() {
        SignalingMessage message = buildMessage();

        retryService.retryPendingDecrypt(message.getUeId(), new UEContext(), reentryHandler);

        verify(pendingDecryptQueue, never()).pollBatch(any(), any(Integer.class));
        verify(decryptCoordinator, never()).tryDecryptByType(any(), any(), any());
        verify(reentryHandler, never()).reenter(any());
    }

    @Test
    @DisplayName("retry should publish batch start event")
    void retryPendingDecryptShouldPublishBatchStartEvent() {
        SignalingMessage message = buildMessage();
        PendingDecryptItem item = PendingDecryptItem.of(
                message.getUeId(),
                message,
                DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS
        );
        UEContext context = buildNasReadyContext();

        when(pendingDecryptQueue.pollBatch(message.getUeId(), 200)).thenReturn(List.of(item));
        when(decryptCoordinator.tryDecryptByType(message, "NAS", context)).thenReturn(DecryptAttemptResult.skip());
        when(pendingDecryptQueue.size(message.getUeId())).thenReturn(1, 1, 1);

        retryService.retryPendingDecrypt(message.getUeId(), context, reentryHandler);

        verify(pendingDecryptEventPublisher, atLeastOnce()).publish(any());
    }

    private SignalingMessage buildMessage() {
        SignalingMessage message = new SignalingMessage();
        message.setUeId("460011234567890");
        message.setMsgId("msg-001");
        message.setMsgType("Security Mode Complete");
        message.setEncryptedType("NAS");
        message.setTimestamp(System.currentTimeMillis());
        message.setFrameNo(42L);
        return message;
    }

    private UEContext buildNasReadyContext() {
        UEContext context = new UEContext();
        context.setKNasEnc("00112233445566778899aabbccddeeff");
        context.setKNasInt("00112233445566778899aabbccddeeff");
        return context;
    }
}
