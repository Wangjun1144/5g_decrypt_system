package com.example.procedure.processing.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.MacInfo;
import com.example.procedure.parser.NgapInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 身份绑定解析器。
 *
 * 这是原 UeIdBinder 的核心逻辑迁移版。
 *
 * 职责：
 * 1. 解析当前消息的 ngapId / rntiType / ueId
 * 2. 判断是否可以立即确定 ueId
 * 3. 在无法确定 ueId 时执行缓冲
 * 4. 在可以确定 ueId 时执行强绑定 / 就近绑定 / pending flush
 * 5. 以 BindingResolutionResult 返回本阶段输出
 *
 * 重要原则：
 * - 功能不变
 * - 先保留原行为
 * - 暂不直接改动下游主链路调用方式
 */
@Service
public class BindingResolver {

    private final BindingStateStore bindingStateStore;
    private final PendingBindingStore pendingBindingStore;
    private final BindingFlushCoordinator flushCoordinator;

    public BindingResolver(
            BindingStateStore bindingStateStore,
            PendingBindingStore pendingBindingStore,
            BindingFlushCoordinator flushCoordinator
    ) {
        this.bindingStateStore = bindingStateStore;
        this.pendingBindingStore = pendingBindingStore;
        this.flushCoordinator = flushCoordinator;
    }

    /**
     * 对单条消息执行身份绑定阶段处理。
     */
    public BindingResolutionResult resolve(SignalingMessage msg) {
        pendingBindingStore.cleanupExpiredPending();

        String ueId = normalize(msg.getUeId());
        String ngapId = extractRanUeNgapId(msg);
        String rntiType = extractRntiType(msg);

        // 1) 优先确定 ueId：消息自带 > ngap 反查 > rntiType 反查
        if (isEmpty(ueId)) {
            ueId = bindingStateStore.lookupUeIdByNgapId(ngapId);
        }
        if (isEmpty(ueId)) {
            ueId = bindingStateStore.lookupUeIdByRntiType(rntiType);
        }

        // 2) 仍不能确定 ueId => 缓冲，不进入下游
        if (isEmpty(ueId)) {
            PendingBindingStore.BufferDecision bufferDecision =
                    pendingBindingStore.buffer(msg, ngapId, rntiType);

            // ueId 先到 / 索引后到的反向绑定尝试
            if (bufferDecision.isBuffered()) {
                if (!isEmpty(bufferDecision.getBufferedNgapId())) {
                    tryBindIncomingNgapToWaitingUe(bufferDecision.getBufferedNgapId());
                } else if (!isEmpty(bufferDecision.getBufferedRntiType())) {
                    tryBindIncomingRntiToWaitingUe(bufferDecision.getBufferedRntiType());
                }
            }

            return BindingResolutionResult.buffered();
        }

        // 3) 能确定 ueId，先补到当前消息上
        msg.setUeId(ueId);

        // 4) 确保这个 ueId 进入等待队列（仅对未绑定索引入队）
        pendingBindingStore.ensureUeInWaitQueuesIfNeeded(
                ueId,
                bindingStateStore.isUeNgapUnbound(ueId),
                bindingStateStore.isUeRntiUnbound(ueId)
        );

        // 5) 优先执行当前消息携带索引的“强绑定”
        boolean boundNgapNow = false;
        boolean boundRntiNow = false;

        List<SignalingMessage> releasedByNgap = List.of();
        List<SignalingMessage> releasedByRnti = List.of();

        if (!isEmpty(ngapId)
                && bindingStateStore.isNgapUnbound(ngapId)
                && bindingStateStore.isUeNgapUnbound(ueId)) {

            bindingStateStore.bindNgapIdToUe(ngapId, ueId);
            pendingBindingStore.removeUeWaitNgap(ueId);
            releasedByNgap = flushCoordinator.flushByNgap(ngapId, ueId);
            boundNgapNow = true;
        }

        if (!isEmpty(rntiType)
                && bindingStateStore.isRntiTypeUnbound(rntiType)
                && bindingStateStore.isUeRntiUnbound(ueId)) {

            bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
            pendingBindingStore.removeUeWaitRnti(ueId);
            releasedByRnti = flushCoordinator.flushByRnti(rntiType, ueId);
            boundRntiNow = true;
        }

        // 6) 若当前消息没带索引，或对应索引已绑定，则尝试“就近绑定”
        if (!boundNgapNow && bindingStateStore.isUeNgapUnbound(ueId)) {
            String candidateNgap = pollFirstReallyUnboundNgap();
            if (candidateNgap != null) {
                bindingStateStore.bindNgapIdToUe(candidateNgap, ueId);
                pendingBindingStore.removeUeWaitNgap(ueId);
                releasedByNgap = flushCoordinator.flushByNgap(candidateNgap, ueId);
            }
        }

        if (!boundRntiNow && bindingStateStore.isUeRntiUnbound(ueId)) {
            String candidateRnti = pollFirstReallyUnboundRntiType();
            if (candidateRnti != null) {
                bindingStateStore.bindRntiTypeToUe(candidateRnti, ueId);
                pendingBindingStore.removeUeWaitRnti(ueId);
                releasedByRnti = flushCoordinator.flushByRnti(candidateRnti, ueId);
            }
        }

        List<SignalingMessage> released =
                flushCoordinator.combineReleased(releasedByNgap, releasedByRnti);

        return BindingResolutionResult.ready(msg, released);
    }

    /**
     * 当某条“只有 ngapId 没有 ueId”的消息到来时，
     * 尝试绑定到最早等待 ngap 的 ueId。
     */
    private void tryBindIncomingNgapToWaitingUe(String ngapId) {
        if (isEmpty(ngapId) || !bindingStateStore.isNgapUnbound(ngapId)) {
            return;
        }

        while (true) {
            String ueId = pendingBindingStore.peekFirstWaitingUeForNgap();
            if (ueId == null) {
                return;
            }

            if (!bindingStateStore.isUeNgapUnbound(ueId)) {
                pendingBindingStore.pollFirstWaitingUeForNgap();
                continue;
            }

            pendingBindingStore.pollFirstWaitingUeForNgap();
            bindingStateStore.bindNgapIdToUe(ngapId, ueId);
            return;
        }
    }

    /**
     * 当某条“只有 rntiType 没有 ueId”的消息到来时，
     * 尝试绑定到最早等待 rntiType 的 ueId。
     */
    private void tryBindIncomingRntiToWaitingUe(String rntiType) {
        if (isEmpty(rntiType) || !bindingStateStore.isRntiTypeUnbound(rntiType)) {
            return;
        }

        while (true) {
            String ueId = pendingBindingStore.peekFirstWaitingUeForRnti();
            if (ueId == null) {
                return;
            }

            if (!bindingStateStore.isUeRntiUnbound(ueId)) {
                pendingBindingStore.pollFirstWaitingUeForRnti();
                continue;
            }

            pendingBindingStore.pollFirstWaitingUeForRnti();
            bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
            return;
        }
    }

    private String pollFirstReallyUnboundNgap() {
        while (true) {
            String ngapId = pendingBindingStore.pollFirstQueuedNgapCandidate();
            if (ngapId == null) {
                return null;
            }

            if (bindingStateStore.isNgapUnbound(ngapId)) {
                return ngapId;
            }

            pendingBindingStore.markNgapDequeued(ngapId);
        }
    }

    private String pollFirstReallyUnboundRntiType() {
        while (true) {
            String rntiType = pendingBindingStore.pollFirstQueuedRntiCandidate();
            if (rntiType == null) {
                return null;
            }

            if (bindingStateStore.isRntiTypeUnbound(rntiType)) {
                return rntiType;
            }

            pendingBindingStore.markRntiDequeued(rntiType);
        }
    }

    private String extractRanUeNgapId(SignalingMessage msg) {
        if (msg == null) {
            return null;
        }
        List<NgapInfo> ngapList = msg.getNgapInfoList();
        if (ngapList == null || ngapList.isEmpty()) {
            return null;
        }
        NgapInfo ngap = ngapList.get(0);
        return ngap == null ? null : normalize(ngap.getRanUeNgapId());
    }

    private String extractRntiType(SignalingMessage msg) {
        if (msg == null) {
            return null;
        }
        MacInfo mac = msg.getMacInfo();
        if (mac == null) {
            return null;
        }
        return normalize(mac.getRntiType());
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}