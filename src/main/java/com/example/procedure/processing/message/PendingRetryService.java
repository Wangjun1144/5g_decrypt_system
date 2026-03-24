package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
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
 */
@Service
public class PendingRetryService {

    private static final Logger log = LoggerFactory.getLogger(PendingRetryService.class);

    private static final int MAX_DECRYPT_DEPTH = 4;

    private final PendingDecryptQueue pendingDecryptQueue;
    private final DecryptCoordinator decryptCoordinator;
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

    private RetryCapability evaluateRetryCapability(UEContext context) {
        boolean canTryNas = nasKeyReady(context);
        boolean canTryRrc = rrcKeyReady(context);
        return new RetryCapability(canTryNas, canTryRrc);
    }

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

    private void handleRetryResult(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        if (decryptResult == null) {
            stats.fail++;
            log.warn("Pending decrypt returned null result(drop): ueId={}, msgId={}, encType={}",
                    originalItem.getUeId(), pendingMessage.getMsgId(), encType);
            return;
        }

        switch (decryptResult.getStatus()) {
            case OK -> handleRetryOk(originalItem, pendingMessage, encType, stats);
            case WAITING -> handleRetryWaiting(originalItem, pendingMessage, decryptResult, stats);
            case FAILED -> handleRetryFailed(originalItem, pendingMessage, encType, decryptResult, stats);
            case SKIP -> handleRetrySkip(stats);
        }
    }

    private void handleRetryOk(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            RetryStats stats
    ) {
        if (safeDecryptDepth(pendingMessage) >= MAX_DECRYPT_DEPTH) {
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
        } catch (Exception e) {
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
    }

    private void handleRetryFailed(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            RetryStats stats
    ) {
        stats.fail++;
        log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                originalItem.getUeId(),
                pendingMessage.getMsgId(),
                encType,
                decryptResult.getError());
    }

    private void handleRetrySkip(RetryStats stats) {
        stats.ok++;
    }

    private void requeueOriginalItem(PendingDecryptItem item, RetryStats stats) {
        pendingDecryptQueue.requeue(item);
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

    private String buildRetrySourceName(SignalingMessage message) {
        String msgId = message == null || message.getMsgId() == null || message.getMsgId().isBlank()
                ? "unknown"
                : message.getMsgId();
        return "pending-retry:" + msgId;
    }

    private String buildRetryCorrelationId(SignalingMessage message) {
        String msgId = message == null || message.getMsgId() == null || message.getMsgId().isBlank()
                ? "unknown"
                : message.getMsgId();
        return "pending-retry-" + msgId;
    }

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

    private static class RetryStats {
        private int ok;
        private int requeue;
        private int fail;
    }
}
