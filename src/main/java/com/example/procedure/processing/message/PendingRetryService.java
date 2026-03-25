package com.example.procedure.processing.message;

import com.example.procedure.application.message.MessageSourceType;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.pending.PendingDecryptEvent;
import com.example.procedure.processing.pending.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.PendingDecryptItem;
import com.example.procedure.processing.pending.PendingDecryptQueue;
import com.example.procedure.support.logging.SignalingDumpWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.List;

/**
 * pending 解密重试服务。
 *
 * 当前阶段重点：
 * - pending 消息正式以 PendingDecryptItem 表达等待状态
 * - retry/reentry 仍保持当前单体同步语义不变
 * - pending retry 生命周期现在会发布正式的内部事件
 */
@Service
public class PendingRetryService {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(PendingRetryService.class);

    /**
     * 防止解密回流无限递归。
     */
    private static final int MAX_DECRYPT_DEPTH = 4;

    /**
     * pending 解密队列。
     */
    private final PendingDecryptQueue pendingDecryptQueue;

    /**
     * 解密协调器。
     */
    private final DecryptCoordinator decryptCoordinator;

    /**
     * 消息主处理器。
     */
    private final MessageProcessor messageProcessor;

    /**
     * pending 事件发布器。
     */
    private final PendingDecryptEventPublisher pendingDecryptEventPublisher;

    /**
     * 构造 pending retry 服务。
     *
     * @param pendingDecryptQueue pending 解密队列
     * @param decryptCoordinator 解密协调器
     * @param messageProcessor 消息主处理器
     * @param pendingDecryptEventPublisher pending 事件发布器
     */
    public PendingRetryService(
            PendingDecryptQueue pendingDecryptQueue,
            DecryptCoordinator decryptCoordinator,
            MessageProcessor messageProcessor,
            PendingDecryptEventPublisher pendingDecryptEventPublisher
    ) {
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.decryptCoordinator = decryptCoordinator;
        this.messageProcessor = messageProcessor;
        this.pendingDecryptEventPublisher = pendingDecryptEventPublisher;
    }

    /**
     * 重试某个 UE 的 pending 解密消息。
     *
     * @param ueId UE 标识
     * @param context 当前 UE 上下文
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
        List<PendingDecryptItem> items = pendingDecryptQueue.pollBatch(ueId, batchSize);
        if (items.isEmpty()) {
            return;
        }

        publishRetryBatchEvent(ueId, items.size());

        RetryStats stats = new RetryStats();

        for (PendingDecryptItem item : items) {
            processPendingItem(context, capability, item, stats);
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
     * 处理单条 pending 消息。
     *
     * @param context 当前 UE 上下文
     * @param capability 当前可重试能力
     * @param item 当前待处理项
     * @param stats 当前统计对象
     */
    private void processPendingItem(
            UEContext context,
            RetryCapability capability,
            PendingDecryptItem item,
            RetryStats stats
    ) {
        SignalingMessage pendingMessage = item.getMessage();
        if (pendingMessage == null) {
            return;
        }

        String encType = normalizeEncType(pendingMessage.getEncryptedType());

        if (!canAttemptForEncType(encType, capability)) {
            requeueOriginalItem(item, stats);
            return;
        }

        DecryptAttemptResult decryptResult =
                decryptCoordinator.tryDecryptByType(pendingMessage, encType, context);

        handleRetryResult(item, pendingMessage, encType, decryptResult, stats);
    }

    /**
     * 根据当前 UE 上下文评估可重试能力。
     *
     * @param context 当前 UE 上下文
     * @return 可重试能力
     */
    private RetryCapability evaluateRetryCapability(UEContext context) {
        boolean canTryNas = nasKeyReady(context);
        boolean canTryRrc = rrcKeyReady(context);
        return new RetryCapability(canTryNas, canTryRrc);
    }

    /**
     * 判断某种加密类型是否可以在当前能力下尝试解密。
     *
     * @param encType 当前加密类型
     * @param capability 当前能力
     * @return true 表示可尝试
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
     * 根据当前解密结果分发不同处理分支。
     *
     * @param originalItem 原始待处理项
     * @param pendingMessage 当前消息
     * @param encType 当前加密类型
     * @param decryptResult 当前解密结果
     * @param stats 当前统计对象
     */
    private void handleRetryResult(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        if (decryptResult == null) {
            stats.fail++;
            publishRetryFailedEvent(originalItem, pendingMessage, encType, "decrypt result is null");
            log.warn("Pending decrypt returned null result(drop): ueId={}, msgId={}, encType={}",
                    originalItem.getUeId(), pendingMessage.getMsgId(), encType);
            return;
        }

        switch (decryptResult.getStatus()) {
            case OK -> handleRetryOk(originalItem, pendingMessage, encType, stats);
            case WAITING -> handleRetryWaiting(originalItem, pendingMessage, decryptResult, stats);
            case FAILED -> handleRetryFailed(originalItem, pendingMessage, encType, decryptResult, stats);
            case SKIP -> handleRetrySkip(originalItem, pendingMessage, encType, stats);
        }
    }

    /**
     * 处理 retry 成功分支。
     *
     * @param originalItem 原始待处理项
     * @param pendingMessage 当前消息
     * @param encType 当前加密类型
     * @param stats 当前统计对象
     */
    private void handleRetryOk(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            RetryStats stats
    ) {
        if (safeDecryptDepth(pendingMessage) >= MAX_DECRYPT_DEPTH) {
            publishRetryFailedEvent(originalItem, pendingMessage, encType, "decrypt max depth reached");
            log.warn("Pending decrypt max-depth reached(drop): ueId={}, msgId={}, encType={}, depth={}",
                    originalItem.getUeId(),
                    pendingMessage.getMsgId(),
                    pendingMessage.getEncryptedType(),
                    safeDecryptDepth(pendingMessage));
            return;
        }

        stats.ok++;

        try {
            MessageProcessRequest reentryRequest = MessageProcessRequest.reentry(
                    pendingMessage,
                    buildRetrySourceName(pendingMessage),
                    buildRetryCorrelationId(pendingMessage)
            );

            decryptCoordinator.handleDecryptSuccess(new MessageProcessingContext(reentryRequest));
            messageProcessor.process(reentryRequest);

            publishRetryOkEvent(originalItem, pendingMessage, encType);
        } catch (Exception e) {
            publishRetryFailedEvent(originalItem, pendingMessage, encType, e.getMessage());
            log.error("Pending decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    pendingMessage.getUeId(),
                    pendingMessage.getMsgId(),
                    encType,
                    e.getMessage(),
                    e);
        }

        log.info("Pending decrypt OK: ueId={}, msgId={}, encType={}",
                originalItem.getUeId(), pendingMessage.getMsgId(), encType);

        SignalingDumpWriter.write(
                pendingMessage,
                Paths.get("logs/signaling_dump_1.log"),
                true
        );
    }

    /**
     * 处理 retry 后仍需继续等待的分支。
     *
     * @param originalItem 原始待处理项
     * @param pendingMessage 当前消息
     * @param decryptResult 当前解密结果
     * @param stats 当前统计对象
     */
    private void handleRetryWaiting(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        PendingDecryptItem newItem = new PendingDecryptItem(
                originalItem.getUeId(),
                System.currentTimeMillis(),
                originalItem.getMsgId(),
                decryptResult.getReason(),
                pendingMessage
        );
        pendingDecryptQueue.requeue(newItem);
        stats.requeue++;

        publishRetryWaitingEvent(newItem, pendingMessage, normalizeEncType(pendingMessage.getEncryptedType()));
    }

    /**
     * 处理 retry 失败分支。
     *
     * @param originalItem 原始待处理项
     * @param pendingMessage 当前消息
     * @param encType 当前加密类型
     * @param decryptResult 当前解密结果
     * @param stats 当前统计对象
     */
    private void handleRetryFailed(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        stats.fail++;
        publishRetryFailedEvent(originalItem, pendingMessage, encType, decryptResult.getError());

        log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                originalItem.getUeId(),
                pendingMessage.getMsgId(),
                encType,
                decryptResult.getError());
    }

    /**
     * 处理 retry skip 分支。
     *
     * @param originalItem 原始待处理项
     * @param pendingMessage 当前消息
     * @param encType 当前加密类型
     * @param stats 当前统计对象
     */
    private void handleRetrySkip(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            RetryStats stats
    ) {
        stats.ok++;
        publishRetrySkipEvent(originalItem, pendingMessage, encType);
    }

    /**
     * 把原始待处理项重新入队。
     *
     * @param item 当前待处理项
     * @param stats 当前统计对象
     */
    private void requeueOriginalItem(PendingDecryptItem item, RetryStats stats) {
        pendingDecryptQueue.requeue(item);
        stats.requeue++;

        publishRetryRequeueEvent(item, item.getMessage(), normalizeEncType(item.getMessage() == null ? null : item.getMessage().getEncryptedType()));
    }

    /**
     * 判断 NAS key 是否已就绪。
     *
     * @param context 当前 UE 上下文
     * @return true 表示就绪
     */
    private boolean nasKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKNasEnc())
                && !isBlank(context.getKNasInt());
    }

    /**
     * 判断 RRC key 是否已就绪。
     *
     * @param context 当前 UE 上下文
     * @return true 表示就绪
     */
    private boolean rrcKeyReady(UEContext context) {
        return context != null
                && !isBlank(context.getKRrcEnc())
                && !isBlank(context.getKRrcInt());
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 输入字符串
     * @return true 表示为空白
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 规范化加密类型。
     *
     * @param encType 原始加密类型
     * @return 规范化后的加密类型
     */
    private String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    /**
     * 安全读取解密深度。
     *
     * @param message 当前消息
     * @return 解密深度
     */
    private int safeDecryptDepth(SignalingMessage message) {
        if (message == null || message.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(message.getDecryptDepth(), 0);
    }

    /**
     * 构造 retry 来源名称。
     *
     * @param message 当前消息
     * @return retry 来源名称
     */
    private String buildRetrySourceName(SignalingMessage message) {
        String msgId = message == null || message.getMsgId() == null || message.getMsgId().isBlank()
                ? "unknown"
                : message.getMsgId();
        return "pending-retry:" + msgId;
    }

    /**
     * 构造 retry 关联 ID。
     *
     * @param message 当前消息
     * @return retry 关联 ID
     */
    private String buildRetryCorrelationId(SignalingMessage message) {
        String msgId = message == null || message.getMsgId() == null || message.getMsgId().isBlank()
                ? "unknown"
                : message.getMsgId();
        return "pending-retry-" + msgId;
    }

    /**
     * 发布 retry 批处理事件。
     *
     * @param ueId UE 标识
     * @param batchSize 本次批大小
     */
    private void publishRetryBatchEvent(String ueId, int batchSize) {
        PendingDecryptEvent event = new PendingDecryptEvent(
                "pending-retry-batch",
                null,
                ueId,
                null,
                null,
                null,
                null,
                MessageSourceType.REENTRY,
                "pending-retry-batch",
                true,
                null,
                null,
                null,
                pendingDecryptQueue.size(ueId),
                batchSize
        );
        pendingDecryptEventPublisher.publish(event);
    }

    /**
     * 发布 retry 成功事件。
     *
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     */
    private void publishRetryOkEvent(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType
    ) {
        pendingDecryptEventPublisher.publish(buildPendingEvent(
                "pending-retry-ok",
                item,
                message,
                encType,
                null,
                item == null ? null : item.getReason()
        ));
    }

    /**
     * 发布 retry 后继续等待事件。
     *
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     */
    private void publishRetryWaitingEvent(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType
    ) {
        pendingDecryptEventPublisher.publish(buildPendingEvent(
                "pending-retry-waiting",
                item,
                message,
                encType,
                null,
                item == null ? null : item.getReason()
        ));
    }

    /**
     * 发布 retry 失败事件。
     *
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     * @param error 错误信息
     */
    private void publishRetryFailedEvent(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType,
            String error
    ) {
        pendingDecryptEventPublisher.publish(buildPendingEvent(
                "pending-retry-failed",
                item,
                message,
                encType,
                error,
                item == null ? null : item.getReason()
        ));
    }

    /**
     * 发布 retry skip 事件。
     *
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     */
    private void publishRetrySkipEvent(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType
    ) {
        pendingDecryptEventPublisher.publish(buildPendingEvent(
                "pending-retry-skip",
                item,
                message,
                encType,
                null,
                item == null ? null : item.getReason()
        ));
    }

    /**
     * 发布原样重新入队事件。
     *
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     */
    private void publishRetryRequeueEvent(
            PendingDecryptItem item,
            SignalingMessage message,
            String encType
    ) {
        pendingDecryptEventPublisher.publish(buildPendingEvent(
                "pending-retry-requeue",
                item,
                message,
                encType,
                null,
                item == null ? null : item.getReason()
        ));
    }

    /**
     * 构造一个统一的 pending decrypt 事件对象。
     *
     * @param action 动作名
     * @param item 当前待处理项
     * @param message 当前消息
     * @param encType 当前加密类型
     * @param error 错误信息
     * @param waitReason 等待原因
     * @return pending decrypt 事件
     */
    private PendingDecryptEvent buildPendingEvent(
            String action,
            PendingDecryptItem item,
            SignalingMessage message,
            String encType,
            String error,
            DecryptAttemptResult.WaitReason waitReason
    ) {
        return new PendingDecryptEvent(
                action,
                buildRetryCorrelationId(message),
                item == null ? null : item.getUeId(),
                message == null ? null : message.getMsgId(),
                message == null ? null : message.getMsgType(),
                message == null ? null : message.getFrameNo(),
                message == null ? null : message.getTimestamp(),
                MessageSourceType.REENTRY,
                buildRetrySourceName(message),
                true,
                waitReason,
                encType,
                error,
                item == null ? null : pendingDecryptQueue.size(item.getUeId()),
                1
        );
    }

    /**
     * retry 能力对象。
     */
    private static class RetryCapability {

        /**
         * 当前是否可以尝试 NAS 解密。
         */
        private final boolean canTryNas;

        /**
         * 当前是否可以尝试 RRC/PDCP 解密。
         */
        private final boolean canTryRrc;

        /**
         * 构造 retry 能力对象。
         *
         * @param canTryNas 是否可尝试 NAS
         * @param canTryRrc 是否可尝试 RRC
         */
        private RetryCapability(boolean canTryNas, boolean canTryRrc) {
            this.canTryNas = canTryNas;
            this.canTryRrc = canTryRrc;
        }

        /**
         * 判断当前是否具备任何重试能力。
         *
         * @return true 表示至少可以重试一种
         */
        private boolean canRetryAnything() {
            return canTryNas || canTryRrc;
        }
    }

    /**
     * retry 统计对象。
     */
    private static class RetryStats {

        /**
         * 成功数。
         */
        private int ok;

        /**
         * 重新入队数。
         */
        private int requeue;

        /**
         * 失败数。
         */
        private int fail;
    }
}
