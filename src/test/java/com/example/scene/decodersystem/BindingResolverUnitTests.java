package com.example.scene.decodersystem;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.processing.binding.resolve.BindingExecutor;
import com.example.procedure.processing.binding.resolve.BindingFlushCoordinator;
import com.example.procedure.processing.binding.resolve.BindingInputExtractor;
import com.example.procedure.processing.binding.resolve.BindingStateStore;
import com.example.procedure.processing.binding.resolve.BindingResolver;
import com.example.procedure.processing.binding.resolve.PendingBindingDecisionHandler;
import com.example.procedure.processing.binding.resolve.PendingBindingReleaseService;
import com.example.procedure.processing.binding.resolve.PendingBindingStore;
import com.example.procedure.processing.binding.resolve.UeIdResolutionPolicy;
import com.example.procedure.processing.binding.resolve.UeWaitQueueRegistrar;
import com.example.procedure.processing.binding.stage.BindingResolutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BindingResolver 鐨勬渶灏忚涓烘祴璇曘€?
 *
 * 杩欑粍娴嬭瘯瀵瑰簲鍓嶉潰绗?12銆?3銆?4 姝ヤ箣鍚庣殑缁戝畾闃舵缁撴瀯锛?
 * - BindingResolver锛氱粦瀹氶樁娈电紪鎺掑櫒
 * - RedisBindingStateStore锛氱粦瀹氱姸鎬佽闂疄鐜? * - InMemoryPendingBindingStore锛氬緟缁戝畾缂撳啿涓庣瓑寰呴槦鍒楀疄鐜? * - BindingFlushCoordinator锛歱ending 閲婃斁鍗忚皟鍣?
 *
 * 褰撳墠娴嬭瘯閲嶇偣涓嶆槸楠岃瘉鎵€鏈夌粦瀹氱粏鑺傦紝
 * 鑰屾槸楠岃瘉 BindingResolver 鐨勫嚑鏉℃牳蹇冪紪鎺掕涔夛細
 *
 * 1. 鏃犳硶纭畾 ueId 鏃讹紝浼氳繘鍏ョ紦鍐插垎鏀?
 * 2. 宸茶兘纭畾 ueId 鏃讹紝浼氱粰褰撳墠娑堟伅琛?ueId
 * 3. 褰撳墠娑堟伅鎼哄甫鍙洿鎺ョ粦瀹氱殑鏂扮储寮曟椂锛屼細浼樺厛鎵ц寮虹粦瀹?
 * 4. 寮虹粦瀹氬悗浼氳Е鍙戝搴旂储寮曠殑 pending flush
 *
 * 褰撳墠娴嬭瘯鏂瑰紡锛?
 * - 涓嶅惎鍔?Spring
 * - 鐩存帴 new BindingResolver
 * - 鎵€鏈変緷璧栦娇鐢?mock
 *
 * 杩欐牱鏇磋交锛屼篃鏇撮€傚悎鏀拺鍚庣画鎸佺画灏忔閲嶆瀯銆?
 */
class BindingResolverUnitTests {
    // REFACTOR STEP: COMPAT_SHELL_PRUNE

    @Mock
    private BindingStateStore bindingStateStore;

    @Mock
    private PendingBindingStore pendingBindingStore;

    @Mock
    private BindingFlushCoordinator flushCoordinator;

    private BindingInputExtractor inputExtractor;
    private UeIdResolutionPolicy ueIdResolutionPolicy;
    private BindingExecutor bindingExecutor;
    private PendingBindingReleaseService pendingBindingReleaseService;
    private PendingBindingDecisionHandler pendingBindingDecisionHandler;
    private UeWaitQueueRegistrar ueWaitQueueRegistrar;
    private BindingResolver bindingResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        inputExtractor = new BindingInputExtractor();
        ueIdResolutionPolicy = new UeIdResolutionPolicy(bindingStateStore);
        bindingExecutor = new BindingExecutor(
                bindingStateStore,
                pendingBindingStore,
                flushCoordinator
        );
        pendingBindingReleaseService = new PendingBindingReleaseService(
                bindingStateStore,
                pendingBindingStore
        );
        pendingBindingDecisionHandler = new PendingBindingDecisionHandler(
                pendingBindingStore,
                pendingBindingReleaseService
        );
        ueWaitQueueRegistrar = new UeWaitQueueRegistrar(
                bindingStateStore,
                pendingBindingStore
        );

        bindingResolver = new BindingResolver(
                pendingBindingStore,
                inputExtractor,
                ueIdResolutionPolicy,
                pendingBindingDecisionHandler,
                ueWaitQueueRegistrar,
                bindingExecutor,
                flushCoordinator
        );
    }

    /**
     * 鏋勯€犱竴鏉″甫 ngapId 鐨勬渶灏忔秷鎭€?
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
     * 鏋勯€犱竴鏉″甫 rntiType 鐨勬渶灏忔秷鎭€?
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
    @DisplayName("鏃犳硶纭畾 ueId 鏃跺簲杩涘叆缂撳啿鍒嗘敮")
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

        // 褰撳墠鍒嗘敮涓嶄細杩涘叆 flush銆?
        verifyNoInteractions(flushCoordinator);
    }

    @Test
    @DisplayName("resolvable ueId should bind ngap immediately")
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
    @DisplayName("娑堟伅鏈韩娌℃湁 ueId锛屼絾鍙€氳繃 ngapId 鍙嶆煡寰楀埌 ueId")
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

        // 鍥犱负璇?ue 宸茬粡涓嶇己 ngap锛屾墍浠ヨ繖閲屼笉搴斿啀鎵ц ngap 寮虹粦瀹氥€?
        verify(bindingStateStore, never()).bindNgapIdToUe(anyString(), anyString());
    }

    @Test
    @DisplayName("resolvable ueId should bind rnti immediately")
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
