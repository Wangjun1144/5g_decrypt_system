package com.example.scene.decodersystem;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.MacInfo;
import com.example.procedure.parser.NgapInfo;
import com.example.procedure.processing.binding.BindingFlushCoordinator;
import com.example.procedure.processing.binding.BindingResolutionResult;
import com.example.procedure.processing.binding.BindingResolver;
import com.example.procedure.processing.binding.BindingStateStore;
import com.example.procedure.processing.binding.PendingBindingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BindingResolver 的最小行为测试。
 *
 * 这组测试对应前面第 12、13、14 步之后的绑定阶段结构：
 * - BindingResolver：绑定阶段编排器
 * - BindingStateStore：绑定状态访问门面
 * - PendingBindingStore：待绑定缓冲与等待队列
 * - BindingFlushCoordinator：pending 释放协调器
 *
 * 当前测试重点不是验证所有绑定细节，
 * 而是验证 BindingResolver 的几条核心编排语义：
 *
 * 1. 无法确定 ueId 时，会进入缓冲分支
 * 2. 已能确定 ueId 时，会给当前消息补 ueId
 * 3. 当前消息携带可直接绑定的新索引时，会优先执行强绑定
 * 4. 强绑定后会触发对应索引的 pending flush
 *
 * 当前测试方式：
 * - 不启动 Spring
 * - 直接 new BindingResolver
 * - 所有依赖使用 mock
 *
 * 这样更轻，也更适合支撑后续持续小步重构。
 */
class BindingResolverUnitTests {

    @Mock
    private BindingStateStore bindingStateStore;

    @Mock
    private PendingBindingStore pendingBindingStore;

    @Mock
    private BindingFlushCoordinator flushCoordinator;

    private BindingResolver bindingResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        bindingResolver = new BindingResolver(
                bindingStateStore,
                pendingBindingStore,
                flushCoordinator
        );
    }

    /**
     * 构造一条带 ngapId 的最小消息。
     */
    private SignalingMessage buildMessageWithNgap(String ueId, String ngapId) {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId(ueId);

        NgapInfo ngapInfo = new NgapInfo();
        ngapInfo.setRanUeNgapId(ngapId);
        msg.setNgapInfoList(List.of(ngapInfo));

        return msg;
    }

    /**
     * 构造一条带 rntiType 的最小消息。
     */
    private SignalingMessage buildMessageWithRnti(String ueId, String rntiType) {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId(ueId);

        MacInfo macInfo = new MacInfo();
        macInfo.setRntiType(rntiType);
        msg.setMacInfo(macInfo);

        return msg;
    }

    @Test
    @DisplayName("无法确定 ueId 时应进入缓冲分支")
    void resolveShouldBufferWhenUeIdCannotBeResolved() {
        SignalingMessage msg = buildMessageWithNgap(null, "RAN-UE-1");

        when(bindingStateStore.lookupUeIdByNgapId("RAN-UE-1")).thenReturn(null);
        when(pendingBindingStore.buffer(msg, "RAN-UE-1", null))
                .thenReturn(PendingBindingStore.BufferDecision.bufferedByNgap("RAN-UE-1"));

        BindingResolutionResult result = bindingResolver.resolve(msg);

        assertNotNull(result);
        assertTrue(result.isBuffered(), "message should be buffered when ueId cannot be resolved");

        verify(pendingBindingStore, times(1)).cleanupExpiredPending();
        verify(bindingStateStore, times(1)).lookupUeIdByNgapId("RAN-UE-1");
        verify(pendingBindingStore, times(1)).buffer(msg, "RAN-UE-1", null);

        // 当前分支不会进入 flush。
        verifyNoInteractions(flushCoordinator);
    }

    @Test
    @DisplayName("消息自带 ueId 且携带未绑定 ngapId 时，应优先执行 ngap 强绑定")
    void resolveShouldBindNgapImmediatelyWhenMessageCarriesResolvableUeId() {
        SignalingMessage msg = buildMessageWithNgap("460011234567890", "RAN-UE-1");

        when(bindingStateStore.isUeNgapUnbound("460011234567890")).thenReturn(true);
        when(bindingStateStore.isUeRntiUnbound("460011234567890")).thenReturn(true);

        when(bindingStateStore.isNgapUnbound("RAN-UE-1")).thenReturn(true);
        when(flushCoordinator.flushByNgap("RAN-UE-1", "460011234567890")).thenReturn(List.of());
        when(flushCoordinator.combineReleased(List.of(), List.of())).thenReturn(List.of());

        BindingResolutionResult result = bindingResolver.resolve(msg);

        assertNotNull(result);
        assertFalse(result.isBuffered(), "resolved message should not be buffered");
        assertEquals("460011234567890", msg.getUeId(), "message should keep resolved ueId");

        verify(pendingBindingStore, times(1)).cleanupExpiredPending();
        verify(pendingBindingStore, times(1))
                .ensureUeInWaitQueuesIfNeeded("460011234567890", true, true);

        verify(bindingStateStore, times(1))
                .bindNgapIdToUe("RAN-UE-1", "460011234567890");
        verify(pendingBindingStore, times(1))
                .removeUeWaitNgap("460011234567890");
        verify(flushCoordinator, times(1))
                .flushByNgap("RAN-UE-1", "460011234567890");
    }

    @Test
    @DisplayName("消息本身没有 ueId，但可通过 ngapId 反查得到 ueId")
    void resolveShouldUseUeIdLookedUpByNgap() {
        SignalingMessage msg = buildMessageWithNgap(null, "RAN-UE-2");

        when(bindingStateStore.lookupUeIdByNgapId("RAN-UE-2")).thenReturn("460011234567891");
        when(bindingStateStore.isUeNgapUnbound("460011234567891")).thenReturn(false);
        when(bindingStateStore.isUeRntiUnbound("460011234567891")).thenReturn(true);

        when(flushCoordinator.combineReleased(List.of(), List.of())).thenReturn(List.of());

        BindingResolutionResult result = bindingResolver.resolve(msg);

        assertNotNull(result);
        assertFalse(result.isBuffered(), "message should be ready after ngap lookup");
        assertEquals("460011234567891", msg.getUeId(), "message should be assigned resolved ueId");

        verify(bindingStateStore, times(1)).lookupUeIdByNgapId("RAN-UE-2");
        verify(pendingBindingStore, times(1))
                .ensureUeInWaitQueuesIfNeeded("460011234567891", false, true);

        // 因为该 ue 已经不缺 ngap，所以这里不应再执行 ngap 强绑定。
        verify(bindingStateStore, never()).bindNgapIdToUe(anyString(), anyString());
    }

    @Test
    @DisplayName("消息自带 ueId 且携带未绑定 rntiType 时，应优先执行 rnti 强绑定")
    void resolveShouldBindRntiImmediatelyWhenMessageCarriesResolvableUeId() {
        SignalingMessage msg = buildMessageWithRnti("460011234567892", "C-RNTI");

        when(bindingStateStore.isUeNgapUnbound("460011234567892")).thenReturn(true);
        when(bindingStateStore.isUeRntiUnbound("460011234567892")).thenReturn(true);

        when(bindingStateStore.isRntiTypeUnbound("C-RNTI")).thenReturn(true);
        when(flushCoordinator.flushByRnti("C-RNTI", "460011234567892")).thenReturn(List.of());
        when(flushCoordinator.combineReleased(List.of(), List.of())).thenReturn(List.of());

        BindingResolutionResult result = bindingResolver.resolve(msg);

        assertNotNull(result);
        assertFalse(result.isBuffered(), "resolved message should not be buffered");
        assertEquals("460011234567892", msg.getUeId());

        verify(pendingBindingStore, times(1))
                .ensureUeInWaitQueuesIfNeeded("460011234567892", true, true);

        verify(bindingStateStore, times(1))
                .bindRntiTypeToUe("C-RNTI", "460011234567892");
        verify(pendingBindingStore, times(1))
                .removeUeWaitRnti("460011234567892");
        verify(flushCoordinator, times(1))
                .flushByRnti("C-RNTI", "460011234567892");
    }
}
