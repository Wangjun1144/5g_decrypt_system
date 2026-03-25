package com.example.procedure.processing.binding;

import com.example.procedure.infrastructure.binding.InMemoryPendingBindingStore;
import com.example.procedure.infrastructure.binding.RedisBindingStateStore;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.MacInfo;
import com.example.procedure.parser.NgapInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 身份绑定解析器。
 *
 * 这是当前“绑定阶段”的核心编排器。
 *
 * 当前职责：
 * 1. 提取当前消息中的绑定线索（ueId / ngapId / rntiType）
 * 2. 判断当前消息是否已经可以确定 ueId
 * 3. 若不能确定，则执行缓冲
 * 4. 若可以确定，则执行：
 *    - 当前消息补齐 ueId
 *    - 强绑定
 *    - 就近绑定
 *    - flush 已释放的 pending 消息
 * 5. 返回统一的 BindingResolutionResult
 */
@Service
public class BindingResolver {

    /**
     * 正式 Redis 绑定状态实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    private final RedisBindingStateStore bindingStateStore;

    /**
     * 正式内存待绑定缓冲状态实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    private final InMemoryPendingBindingStore pendingBindingStore;

    /**
     * 待绑定消息释放协调器。
     */
    private final BindingFlushCoordinator flushCoordinator;

    /**
     * 构造绑定解析器。
     *
     * @param bindingStateStore 正式 Redis 绑定状态实现
     * @param pendingBindingStore 正式内存待绑定缓冲状态实现
     * @param flushCoordinator 待绑定消息释放协调器
     */
    public BindingResolver(
            RedisBindingStateStore bindingStateStore,
            InMemoryPendingBindingStore pendingBindingStore,
            BindingFlushCoordinator flushCoordinator
    ) {
        this.bindingStateStore = bindingStateStore;
        this.pendingBindingStore = pendingBindingStore;
        this.flushCoordinator = flushCoordinator;
    }

    /**
     * 对单条消息执行绑定阶段处理。
     *
     * @param msg 当前消息
     * @return 绑定阶段结果
     */
    public BindingResolutionResult resolve(SignalingMessage msg) {
        pendingBindingStore.cleanupExpiredPending();

        BindingInputs inputs = extractBindingInputs(msg);
        String resolvedUeId = resolveUeId(inputs);

        if (isEmpty(resolvedUeId)) {
            return handleUnresolvedMessage(msg, inputs);
        }

        return handleResolvedMessage(msg, inputs, resolvedUeId);
    }

    /**
     * 提取当前消息中的绑定输入要素。
     *
     * @param msg 当前消息
     * @return 绑定输入对象
     */
    private BindingInputs extractBindingInputs(SignalingMessage msg) {
        String ueId = normalize(msg.getUeId());
        String ngapId = extractRanUeNgapId(msg);
        String rntiType = extractRntiType(msg);
        return new BindingInputs(ueId, ngapId, rntiType);
    }

    /**
     * 解析本轮消息最终应使用的 ueId。
     *
     * @param inputs 绑定输入
     * @return 解析出的 ueId
     */
    private String resolveUeId(BindingInputs inputs) {
        if (!isEmpty(inputs.ueId())) {
            return inputs.ueId();
        }

        String ueIdByNgap = bindingStateStore.lookupUeIdByNgapId(inputs.ngapId());
        if (!isEmpty(ueIdByNgap)) {
            return ueIdByNgap;
        }

        return bindingStateStore.lookupUeIdByRntiType(inputs.rntiType());
    }

    /**
     * 处理“当前消息仍无法确定 ueId”的分支。
     *
     * @param msg 当前消息
     * @param inputs 绑定输入
     * @return 绑定阶段结果
     */
    private BindingResolutionResult handleUnresolvedMessage(SignalingMessage msg, BindingInputs inputs) {
        InMemoryPendingBindingStore.BufferDecision bufferDecision =
                pendingBindingStore.buffer(msg, inputs.ngapId(), inputs.rntiType());

        if (bufferDecision.isBuffered()) {
            if (!isEmpty(bufferDecision.getBufferedNgapId())) {
                tryBindIncomingNgapToWaitingUe(bufferDecision.getBufferedNgapId());
            } else if (!isEmpty(bufferDecision.getBufferedRntiType())) {
                tryBindIncomingRntiToWaitingUe(bufferDecision.getBufferedRntiType());
            }
        }

        return BindingResolutionResult.buffered();
    }

    /**
     * 处理“当前消息已经可以确定 ueId”的分支。
     *
     * @param msg 当前消息
     * @param inputs 绑定输入
     * @param ueId 已解析的 ueId
     * @return 绑定阶段结果
     */
    private BindingResolutionResult handleResolvedMessage(
            SignalingMessage msg,
            BindingInputs inputs,
            String ueId
    ) {
        msg.setUeId(ueId);

        ensureUeWaitQueues(ueId);

        BindingExecution execution = executeBinding(msg, inputs, ueId);

        List<SignalingMessage> released =
                flushCoordinator.combineReleased(
                        execution.releasedByNgap(),
                        execution.releasedByRnti()
                );

        return BindingResolutionResult.ready(msg, released);
    }

    /**
     * 确保当前 ueId 进入“等待索引补齐”的队列。
     *
     * @param ueId ueId
     */
    private void ensureUeWaitQueues(String ueId) {
        pendingBindingStore.ensureUeInWaitQueuesIfNeeded(
                ueId,
                bindingStateStore.isUeNgapUnbound(ueId),
                bindingStateStore.isUeRntiUnbound(ueId)
        );
    }

    /**
     * 执行真正的绑定动作。
     *
     * @param msg 当前消息
     * @param inputs 绑定输入
     * @param ueId ueId
     * @return 绑定执行结果
     */
    private BindingExecution executeBinding(SignalingMessage msg, BindingInputs inputs, String ueId) {
        boolean boundNgapNow = false;
        boolean boundRntiNow = false;

        List<SignalingMessage> releasedByNgap = List.of();
        List<SignalingMessage> releasedByRnti = List.of();

        if (canBindNgapNow(inputs.ngapId(), ueId)) {
            bindNgapToUe(inputs.ngapId(), ueId);
            releasedByNgap = flushCoordinator.flushByNgap(inputs.ngapId(), ueId);
            boundNgapNow = true;
        }

        if (canBindRntiNow(inputs.rntiType(), ueId)) {
            bindRntiToUe(inputs.rntiType(), ueId);
            releasedByRnti = flushCoordinator.flushByRnti(inputs.rntiType(), ueId);
            boundRntiNow = true;
        }

        if (!boundNgapNow && bindingStateStore.isUeNgapUnbound(ueId)) {
            String candidateNgap = pollFirstReallyUnboundNgap();
            if (candidateNgap != null) {
                bindNgapToUe(candidateNgap, ueId);
                releasedByNgap = flushCoordinator.flushByNgap(candidateNgap, ueId);
            }
        }

        if (!boundRntiNow && bindingStateStore.isUeRntiUnbound(ueId)) {
            String candidateRnti = pollFirstReallyUnboundRntiType();
            if (candidateRnti != null) {
                bindRntiToUe(candidateRnti, ueId);
                releasedByRnti = flushCoordinator.flushByRnti(candidateRnti, ueId);
            }
        }

        return new BindingExecution(releasedByNgap, releasedByRnti);
    }

    /**
     * 判断当前 ngapId 是否可立即绑定。
     *
     * @param ngapId ngapId
     * @param ueId ueId
     * @return true 表示可立即绑定
     */
    private boolean canBindNgapNow(String ngapId, String ueId) {
        return !isEmpty(ngapId)
                && bindingStateStore.isNgapUnbound(ngapId)
                && bindingStateStore.isUeNgapUnbound(ueId);
    }

    /**
     * 判断当前 rntiType 是否可立即绑定。
     *
     * @param rntiType rntiType
     * @param ueId ueId
     * @return true 表示可立即绑定
     */
    private boolean canBindRntiNow(String rntiType, String ueId) {
        return !isEmpty(rntiType)
                && bindingStateStore.isRntiTypeUnbound(rntiType)
                && bindingStateStore.isUeRntiUnbound(ueId);
    }

    /**
     * 执行 ngapId -> ueId 绑定。
     *
     * @param ngapId ngapId
     * @param ueId ueId
     */
    private void bindNgapToUe(String ngapId, String ueId) {
        bindingStateStore.bindNgapIdToUe(ngapId, ueId);
        pendingBindingStore.removeUeWaitNgap(ueId);
    }

    /**
     * 执行 rntiType -> ueId 绑定。
     *
     * @param rntiType rntiType
     * @param ueId ueId
     */
    private void bindRntiToUe(String rntiType, String ueId) {
        bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
        pendingBindingStore.removeUeWaitRnti(ueId);
    }

    /**
     * 尝试把传入 ngapId 绑定给等待 ngap 的 ue。
     *
     * @param ngapId ngapId
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
     * 尝试把传入 rntiType 绑定给等待 rnti 的 ue。
     *
     * @param rntiType rntiType
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

    /**
     * 弹出第一个真正仍未绑定的 ngap 候选。
     *
     * @return ngapId
     */
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

    /**
     * 弹出第一个真正仍未绑定的 rnti 候选。
     *
     * @return rntiType
     */
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

    /**
     * 从消息中提取 ranUeNgapId。
     *
     * @param msg 当前消息
     * @return ranUeNgapId
     */
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

    /**
     * 从消息中提取 rntiType。
     *
     * @param msg 当前消息
     * @return rntiType
     */
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

    /**
     * 判断字符串是否为空。
     *
     * @param s 输入字符串
     * @return true 表示为空
     */
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 规范化字符串。
     *
     * @param s 输入字符串
     * @return 规范化后的字符串
     */
    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }

    /**
     * 绑定输入对象。
     */
    private record BindingInputs(String ueId, String ngapId, String rntiType) {
    }

    /**
     * 绑定执行结果。
     */
    private record BindingExecution(
            List<SignalingMessage> releasedByNgap,
            List<SignalingMessage> releasedByRnti
    ) {
    }
}
