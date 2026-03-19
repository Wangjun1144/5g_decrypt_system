package com.example.procedure.processing.binding;

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
 *    - 强绑定（当前消息携带索引时优先绑定）
 *    - 就近绑定（当前消息没有新索引时，从等待队列中补位）
 *    - flush 已释放的 pending 消息
 * 5. 返回统一的 BindingResolutionResult
 *
 * 第 12 小步的重构重点：
 * - 不改变现有绑定行为
 * - 把长方法中的多个阶段拆成清晰的私有方法
 * - 让 BindingResolver 更像“绑定阶段编排器”
 *
 * 当前阶段仍保留的重要语义：
 * - 优先使用消息自带 ueId
 * - 其次按 ngapId 反查 ueId
 * - 再按 rntiType 反查 ueId
 * - 无法确定 ueId 时优先按 ngapId 缓冲，否则按 rntiType 缓冲
 * - 若当前消息可处理，则先释放 pending，再处理当前消息
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
     * 对单条消息执行绑定阶段处理。
     *
     * 当前总体流程：
     * 1. 清理过期 pending
     * 2. 提取当前消息中的绑定线索
     * 3. 尝试解析 ueId
     * 4. 若仍无法确定 ueId，则缓冲消息
     * 5. 若可以确定 ueId，则执行绑定与 flush
     */
    public BindingResolutionResult resolve(SignalingMessage msg) {
        pendingBindingStore.cleanupExpiredPending();

        BindingInputs inputs = extractBindingInputs(msg);
        String resolvedUeId = resolveUeId(inputs);

        // 当前仍无法确定 ueId：进入缓冲分支。
        if (isEmpty(resolvedUeId)) {
            return handleUnresolvedMessage(msg, inputs);
        }

        // 当前已经可以确定 ueId：进入正式绑定分支。
        return handleResolvedMessage(msg, inputs, resolvedUeId);
    }

    /**
     * 提取当前消息中的绑定输入要素。
     *
     * 这一步只负责“读取线索”，不承担任何绑定决策。
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
     * 当前优先级保持现有语义不变：
     * 1. 消息自带 ueId
     * 2. 通过 ngapId 反查 ueId
     * 3. 通过 rntiType 反查 ueId
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
     * 当前语义保持不变：
     * - 优先按 ngapId 缓冲
     * - 否则按 rntiType 缓冲
     * - 若缓冲成功，则尝试做一次“索引反向绑定”
     */
    private BindingResolutionResult handleUnresolvedMessage(SignalingMessage msg, BindingInputs inputs) {
        PendingBindingStore.BufferDecision bufferDecision =
                pendingBindingStore.buffer(msg, inputs.ngapId(), inputs.rntiType());

        // 当前消息被缓冲后，如果它带来了新的索引，
        // 则尝试与“ueId 先到、索引后到”的等待队列做一次对接。
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
     * 当前流程保持不变：
     * 1. 给当前消息补 ueId
     * 2. 确保该 ueId 进入相应等待队列
     * 3. 优先执行当前消息携带索引的强绑定
     * 4. 若未发生强绑定，再尝试就近绑定
     * 5. flush 被释放的历史 pending
     * 6. 返回 ready 结果
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
     * 当前语义保持不变：
     * - 如果 ueId 尚未绑定 ngap，则进入 ngap 等待队列
     * - 如果 ueId 尚未绑定 rntiType，则进入 rnti 等待队列
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
     * 当前策略分两层：
     *
     * 第一层：强绑定
     * - 当前消息如果带有新的 ngapId / rntiType，并且它们尚未绑定
     * - 则优先将这些索引直接绑定到当前 ueId
     *
     * 第二层：就近绑定
     * - 如果当前消息没有提供可强绑定的新索引
     * - 则尝试从等待索引队列中取一个尚未绑定的索引，补给当前 ueId
     */
    private BindingExecution executeBinding(SignalingMessage msg, BindingInputs inputs, String ueId) {
        boolean boundNgapNow = false;
        boolean boundRntiNow = false;

        List<SignalingMessage> releasedByNgap = List.of();
        List<SignalingMessage> releasedByRnti = List.of();

        // 优先执行当前消息携带索引的强绑定。
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

        // 如果当前消息没带来新的可用索引，则尝试做就近绑定。
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
     * 判断当前消息的 ngapId 是否可以立即绑定到当前 ueId。
     */
    private boolean canBindNgapNow(String ngapId, String ueId) {
        return !isEmpty(ngapId)
                && bindingStateStore.isNgapUnbound(ngapId)
                && bindingStateStore.isUeNgapUnbound(ueId);
    }

    /**
     * 判断当前消息的 rntiType 是否可以立即绑定到当前 ueId。
     */
    private boolean canBindRntiNow(String rntiType, String ueId) {
        return !isEmpty(rntiType)
                && bindingStateStore.isRntiTypeUnbound(rntiType)
                && bindingStateStore.isUeRntiUnbound(ueId);
    }

    /**
     * 执行 ngapId -> ueId 绑定，并同步移除 ue 的 ngap 等待状态。
     */
    private void bindNgapToUe(String ngapId, String ueId) {
        bindingStateStore.bindNgapIdToUe(ngapId, ueId);
        pendingBindingStore.removeUeWaitNgap(ueId);
    }

    /**
     * 执行 rntiType -> ueId 绑定，并同步移除 ue 的 rnti 等待状态。
     */
    private void bindRntiToUe(String rntiType, String ueId) {
        bindingStateStore.bindRntiTypeToUe(rntiType, ueId);
        pendingBindingStore.removeUeWaitRnti(ueId);
    }

    /**
     * 当某条“只有 ngapId、没有 ueId”的消息到来时，
     * 尝试把这个 ngapId 绑定给最早等待 ngap 的 ueId。
     *
     * 当前语义保持不变：
     * - 跳过已经不再缺 ngap 的 ueId
     * - 一旦成功绑定一个 ueId，立即返回
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
     * 当某条“只有 rntiType、没有 ueId”的消息到来时，
     * 尝试把这个 rntiType 绑定给最早等待 rntiType 的 ueId。
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
     * 从“未绑定 ngap 候选队列”中取出第一个真正仍未绑定的 ngapId。
     *
     * 这里要跳过那些虽然还在队列中，
     * 但实际上已经在别处完成绑定的历史候选项。
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
     * 从“未绑定 rnti 候选队列”中取出第一个真正仍未绑定的 rntiType。
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
     * 当前仍沿用现有语义：
     * - 若存在多个 NGAP 信息，取第一个
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

    /**
     * 当前消息中与绑定相关的输入线索。
     *
     * 这样做的目的：
     * - 避免在 resolve(...) 主流程中反复传多个字符串
     * - 让“提取线索”和“执行绑定决策”之间的边界更清晰
     */
    private record BindingInputs(String ueId, String ngapId, String rntiType) {
    }

    /**
     * 本轮绑定执行结果。
     *
     * 当前只记录：
     * - 通过 ngap flush 释放的消息
     * - 通过 rnti flush 释放的消息
     */
    private record BindingExecution(
            List<SignalingMessage> releasedByNgap,
            List<SignalingMessage> releasedByRnti
    ) {
    }
}
