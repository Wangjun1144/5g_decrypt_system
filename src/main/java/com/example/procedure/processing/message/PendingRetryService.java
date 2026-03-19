package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import com.example.procedure.processing.pending.PendingMessageRecord;
import com.example.procedure.support.logging.SignalingDumpWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.List;

/**
 * pending 解密重试服务。
 *
 * 职责：
 * 1. 当上下文中的 key / 算法变得可用时，批量重试某个 UE 的待解密消息
 * 2. 对每条待重试消息统一处理 OK / WAITING / FAILED / SKIP 分支
 * 3. 在解密成功后，让消息重新进入完整主链
 *
 * 第 11 小步的重构重点：
 * - 不改变当前重试语义
 * - 把长方法中的分支判断与动作拆成更清晰的私有方法
 * - 让 PendingRetryService 更像“重试阶段编排器”
 *
 * 当前阶段仍保留的关键语义：
 * - 只在当前上下文具备条件时尝试对应类型的解密
 * - 解密成功后先回流，再重新进入完整主链
 * - WAITING 重新入队
 * - FAILED 直接丢弃并记录日志
 * - SKIP 视为当前无需解密，直接成功离队
 *
 * 后续演进方向：
 * - retry 次数控制
 * - backoff / dead-letter
 * - retry topic / waiting topic
 * - 独立 retry worker
 */
@Service
public class PendingRetryService {

    private static final Logger log = LoggerFactory.getLogger(PendingRetryService.class);

    /**
     * 防止 pending 消息在多轮回流后进入无限递归。
     */
    private static final int MAX_DECRYPT_DEPTH = 4;

    /**
     * 当前仍依赖新的 pending 队列边界，而不是旧 PendingMessageService。
     */
    private final PendingDecryptQueue pendingDecryptQueue;

    /**
     * 当前仍直接依赖底层解密协调器。
     *
     * 原因：
     * - PendingRetryService 不只是“是否需要解密”
     * - 它还需要直接执行按类型重试 tryDecryptByType(...)
     * - 当前阶段先保持这一层不再额外抽象，避免改动过深
     */
    private final DecryptCoordinator decryptCoordinator;

    /**
     * 解密成功后，仍然让消息重新进入完整主链。
     *
     * 这是当前单体架构下最稳定的兼容做法，
     * 后续如果进一步演进为事件驱动，可以再逐步替换。
     */
    private final MessageProcessor messageProcessor;

    public PendingRetryService(
            PendingDecryptQueue pendingDecryptQueue,
            DecryptCoordinator decryptCoordinator,
            MessageProcessor messageProcessor
    ) {
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.decryptCoordinator = decryptCoordinator;
        this.messageProcessor = messageProcessor;
    }

    /**
     * 在当前 UE 上下文变得更完整之后，
     * 尝试重试该 UE 下的 pending 解密消息。
     *
     * 当前策略：
     * 1. 如果当前上下文不具备任何解密条件，则直接返回
     * 2. 批量拉取 pending 消息
     * 3. 对每条消息执行统一重试分支
     * 4. 记录本轮统计日志
     */
    public void retryPendingDecrypt(String ueId, UEContext context) {
        if (isBlank(ueId)) {
            return;
        }

        RetryCapability capability = evaluateRetryCapability(context);
        if (!capability.canRetryAnything()) {
            return;
        }

        int batchSize = 200;
        List<PendingMessageRecord> items = pendingDecryptQueue.pollBatch(ueId, batchSize);
        if (items.isEmpty()) {
            return;
        }

        RetryStats stats = new RetryStats();

        for (PendingMessageRecord item : items) {
            processPendingItem(ueId, context, capability, item, stats);
        }

        log.info("Pending decrypt retry done: ueId={}, batch={}, ok={}, requeue={}, fail={}, remain={}",
                ueId,
                items.size(),
                stats.ok,
                stats.requeue,
                stats.fail,
                pendingDecryptQueue.size(ueId));
    }

    /**
     * 处理一条 pending 消息。
     *
     * 这里将“是否可尝试、尝试后如何分支处理”收口到单独方法，
     * 让 retryPendingDecrypt(...) 主流程更清晰。
     */
    private void processPendingItem(
            String ueId,
            UEContext context,
            RetryCapability capability,
            PendingMessageRecord item,
            RetryStats stats
    ) {
        SignalingMessage pendingMessage = item.getMessage();
        if (pendingMessage == null) {
            return;
        }

        String encType = normalizeEncType(pendingMessage.getEncryptedType());

        if (!canAttemptForEncType(encType, capability)) {
            requeueOriginalRecord(ueId, item, stats);
            return;
        }

        DecryptAttemptResult decryptResult =
                decryptCoordinator.tryDecryptByType(pendingMessage, encType, context);

        handleRetryResult(ueId, item, pendingMessage, encType, decryptResult, stats);
    }

    /**
     * 根据当前 UEContext 判断本轮具备哪些重试能力。
     *
     * 当前粒度仍保持简单：
     * - NAS 可试
     * - RRC/PDCP 可试
     */
    private RetryCapability evaluateRetryCapability(UEContext context) {
        boolean canTryNas = nasKeyReady(context);
        boolean canTryRrc = rrcKeyReady(context);
        return new RetryCapability(canTryNas, canTryRrc);
    }

    /**
     * 判断指定加密类型在当前能力下是否可以发起重试。
     *
     * 当前规则保持不变：
     * - NAS 需要 canTryNas
     * - PDCP 需要 canTryRrc
     * - NAS+PDCP 只要任一侧可试，就允许进入 tryDecryptByType
     * - 其他类型默认允许继续交给底层逻辑判断
     */
    private boolean canAttemptForEncType(String encType, RetryCapability capability) {
        if ("NAS".equals(encType)) {
            return capability.canTryNas;
        }

        if ("PDCP".equals(encType)) {
            return capability.canTryRrc;
        }

        if ("NAS+PDCP".equals(encType)) {
            return capability.canTryNas || capability.canTryRrc;
        }

        return true;
    }

    /**
     * 统一处理一次重试尝试后的结果分支。
     */
    private void handleRetryResult(
            String ueId,
            PendingMessageRecord originalRecord,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        if (decryptResult == null) {
            stats.fail++;
            log.warn("Pending decrypt returned null result(drop): ueId={}, msgId={}, encType={}",
                    ueId, pendingMessage.getMsgId(), encType);
            return;
        }

        switch (decryptResult.getStatus()) {
            case OK -> handleRetryOk(ueId, pendingMessage, encType, stats);
            case WAITING -> handleRetryWaiting(ueId, originalRecord, pendingMessage, decryptResult, stats);
            case FAILED -> handleRetryFailed(ueId, pendingMessage, encType, decryptResult, stats);
            case SKIP -> handleRetrySkip(stats);
        }
    }

    /**
     * 解密成功后的处理。
     *
     * 当前语义保持不变：
     * 1. 先执行回流
     * 2. 再重新进入完整主链
     */
    private void handleRetryOk(
            String ueId,
            SignalingMessage pendingMessage,
            String encType,
            RetryStats stats
    ) {
        if (safeDecryptDepth(pendingMessage) >= MAX_DECRYPT_DEPTH) {
            log.warn("Pending decrypt max-depth reached(drop): ueId={}, msgId={}, encType={}, depth={}",
                    ueId,
                    pendingMessage.getMsgId(),
                    pendingMessage.getEncryptedType(),
                    safeDecryptDepth(pendingMessage));
            return;
        }

        stats.ok++;

        try {
            decryptCoordinator.handleDecryptSuccess(new MessageProcessingContext(pendingMessage));

            // 保持当前系统行为：
            // 回流后重新进入完整主链，确保分类、流程识别、分发、上下文更新都不会被跳过。
            messageProcessor.process(pendingMessage);
        } catch (Exception e) {
            log.error("Pending decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    pendingMessage.getUeId(),
                    pendingMessage.getMsgId(),
                    encType,
                    e.getMessage(),
                    e);
        }

        log.info("Pending decrypt OK: ueId={}, msgId={}, encType={}",
                ueId, pendingMessage.getMsgId(), encType);

        // 保留现有调试输出行为，便于和旧日志比对。
        SignalingDumpWriter.write(
                pendingMessage,
                Paths.get("logs/signaling_dump_1.log"),
                true
        );
    }

    /**
     * 当前轮仍然缺少解密条件时，重新入队。
     *
     * 注意：
     * - 这里保留“重新入队时更新时间戳”的现有语义
     * - msgId 仍沿用原记录，便于追踪同一条消息的重试轨迹
     */
    private void handleRetryWaiting(
            String ueId,
            PendingMessageRecord originalRecord,
            SignalingMessage pendingMessage,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        PendingMessageRecord newRecord = new PendingMessageRecord(
                System.currentTimeMillis(),
                originalRecord.getMsgId(),
                decryptResult.getReason(),
                pendingMessage
        );
        pendingDecryptQueue.requeue(ueId, newRecord);
        stats.requeue++;
    }

    /**
     * 解密失败后的处理。
     *
     * 当前仍保持“记录告警并丢弃”的语义，
     * 暂不引入 dead-letter 或失败重试策略。
     */
    private void handleRetryFailed(
            String ueId,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        stats.fail++;
        log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                ueId,
                pendingMessage.getMsgId(),
                encType,
                decryptResult.getError());
    }

    /**
     * SKIP 表示当前消息无需解密。
     *
     * 当前仍将其视为成功离队。
     */
    private void handleRetrySkip(RetryStats stats) {
        stats.ok++;
    }

    /**
     * 对于本轮根本不具备尝试条件的消息，直接按原记录回队。
     *
     * 这种情况和 WAITING 不完全一样：
     * - WAITING 是“已经尝试过，底层判断仍缺条件”
     * - 这里是“上层就已知本轮无须尝试”
     */
    private void requeueOriginalRecord(String ueId, PendingMessageRecord item, RetryStats stats) {
        pendingDecryptQueue.requeue(ueId, item);
        stats.requeue++;
    }

    private boolean nasKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKNasEnc())
                && !isBlank(context.getKNasInt());
    }

    private boolean rrcKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKRrcEnc())
                && !isBlank(context.getKRrcInt());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    private int safeDecryptDepth(SignalingMessage message) {
        if (message == null || message.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(message.getDecryptDepth(), 0);
    }

    /**
     * 当前轮可重试能力快照。
     *
     * 把“NAS 能否尝试 / RRC 能否尝试”打包成一个值对象，
     * 这样后续如果扩展更多能力维度，不需要在主流程里继续散落布尔变量。
     */
    private static class RetryCapability {
        private final boolean canTryNas;
        private final boolean canTryRrc;

        private RetryCapability(boolean canTryNas, boolean canTryRrc) {
            this.canTryNas = canTryNas;
            this.canTryRrc = canTryRrc;
        }

        private boolean canRetryAnything() {
            return canTryNas || canTryRrc;
        }
    }

    /**
     * 本轮批处理统计。
     *
     * 当前只统计：
     * - ok
     * - requeue
     * - fail
     */
    private static class RetryStats {
        private int ok;
        private int requeue;
        private int fail;
    }
}
