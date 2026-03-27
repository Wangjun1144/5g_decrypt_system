package com.example.procedure.processing.message.retry;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.decrypt.MessageDecryptCoordinator;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.processing.pending.queue.PendingDecryptItem;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import com.example.procedure.support.logging.SignalingDumpWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * Executes retry logic for one pending decrypt item.
 */
@Component
public class PendingDecryptItemRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(PendingDecryptItemRetryExecutor.class);

    private final PendingDecryptQueue pendingDecryptQueue;
    private final MessageDecryptCoordinator decryptCoordinator;
    private final PendingDecryptRetryPolicy retryPolicy;
    private final PendingDecryptRetryIdentityFactory retryIdentityFactory;
    private final PendingDecryptItemEventPublisher retryEventPublisher;

    public PendingDecryptItemRetryExecutor(
            PendingDecryptQueue pendingDecryptQueue,
            MessageDecryptCoordinator decryptCoordinator,
            PendingDecryptRetryPolicy retryPolicy,
            PendingDecryptRetryIdentityFactory retryIdentityFactory,
            PendingDecryptItemEventPublisher retryEventPublisher
    ) {
        this.pendingDecryptQueue = pendingDecryptQueue;
        this.decryptCoordinator = decryptCoordinator;
        this.retryPolicy = retryPolicy;
        this.retryIdentityFactory = retryIdentityFactory;
        this.retryEventPublisher = retryEventPublisher;
    }

    /**
     * Executes one queued item and returns a tiny accounting object back to the batch orchestrator.
     */
    public PendingDecryptItemRetryResult execute(
            UEContext context,
            PendingDecryptRetryService.RetryCapability capability,
            PendingDecryptItem item,
            PendingDecryptReentryHandler reentryHandler
    ) {
        SignalingMessage pendingMessage = item.getMessage();
        // Empty payloads are ignored because there is nothing meaningful left to retry.
        if (pendingMessage == null) {
            return PendingDecryptItemRetryResult.none();
        }

        String encType = retryPolicy.normalizeEncType(pendingMessage.getEncryptedType());
        // Capability gating prevents us from attempting decrypt families we still cannot unlock.
        if (!retryPolicy.canAttemptForEncType(encType, capability)) {
            pendingDecryptQueue.requeue(item);
            retryEventPublisher.publishRetryRequeue(item, pendingMessage, encType);
            return PendingDecryptItemRetryResult.requeued();
        }

        DecryptAttemptResult decryptResult =
                decryptCoordinator.tryDecryptByType(pendingMessage, encType, context);

        return handleRetryResult(item, pendingMessage, encType, decryptResult, reentryHandler);
    }

    /**
     * Dispatches handling by decrypt result status so branch-specific side effects stay isolated.
     */
    private PendingDecryptItemRetryResult handleRetryResult(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult,
            PendingDecryptReentryHandler reentryHandler
    ) {
        if (decryptResult == null) {
            retryEventPublisher.publishRetryFailed(originalItem, pendingMessage, encType, "decrypt result is null");
            log.warn("Pending decrypt returned null result(drop): ueId={}, msgId={}, encType={}",
                    originalItem.getUeId(), pendingMessage.getMsgId(), encType);
            return PendingDecryptItemRetryResult.failure();
        }

        return switch (decryptResult.getStatus()) {
            case OK -> handleRetryOk(originalItem, pendingMessage, encType, reentryHandler);
            case WAITING -> handleRetryWaiting(originalItem, pendingMessage, decryptResult);
            case FAILED -> handleRetryFailed(originalItem, pendingMessage, encType, decryptResult);
            case SKIP -> handleRetrySkip(originalItem, pendingMessage, encType);
        };
    }

    /**
     * Handles the successful retry branch, including reentry and post-success dump writing.
     */
    private PendingDecryptItemRetryResult handleRetryOk(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            PendingDecryptReentryHandler reentryHandler
    ) {
        if (retryPolicy.hasReachedMaxDepth(pendingMessage)) {
            retryEventPublisher.publishRetryFailed(originalItem, pendingMessage, encType, "decrypt max depth reached");
            log.warn("Pending decrypt max-depth reached(drop): ueId={}, msgId={}, encType={}, depth={}",
                    originalItem.getUeId(),
                    pendingMessage.getMsgId(),
                    pendingMessage.getEncryptedType(),
                    retryPolicy.safeDecryptDepth(pendingMessage));
            return PendingDecryptItemRetryResult.failure();
        }

        try {
            MessageProcessingRequest reentryRequest = MessageProcessingRequest.reentry(
                    pendingMessage,
                    retryIdentityFactory.buildRetrySourceName(pendingMessage),
                    retryIdentityFactory.buildRetryCorrelationId(pendingMessage)
            );

            decryptCoordinator.handleDecryptSuccess(new MessageProcessingContext(reentryRequest));
            reentryHandler.reenter(reentryRequest);
            retryEventPublisher.publishRetryOk(originalItem, pendingMessage, encType);
        } catch (Exception e) {
            retryEventPublisher.publishRetryFailed(originalItem, pendingMessage, encType, e.getMessage());
            log.error("Pending decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    pendingMessage.getUeId(),
                    pendingMessage.getMsgId(),
                    encType,
                    e.getMessage(),
                    e);
            return PendingDecryptItemRetryResult.failure();
        }

        log.info("Pending decrypt OK: ueId={}, msgId={}, encType={}",
                originalItem.getUeId(), pendingMessage.getMsgId(), encType);

        SignalingDumpWriter.write(
                pendingMessage,
                Paths.get("logs/signaling_dump_1.log"),
                true
        );

        return PendingDecryptItemRetryResult.success();
    }

    /**
     * Requeues the item with the new wait reason when keys or algorithms are still incomplete.
     */
    private PendingDecryptItemRetryResult handleRetryWaiting(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            DecryptAttemptResult decryptResult
    ) {
        PendingDecryptItem newItem = new PendingDecryptItem(
                originalItem.getUeId(),
                System.currentTimeMillis(),
                originalItem.getMsgId(),
                decryptResult.getReason(),
                pendingMessage
        );
        pendingDecryptQueue.requeue(newItem);
        retryEventPublisher.publishRetryWaiting(
                newItem,
                pendingMessage,
                retryPolicy.normalizeEncType(pendingMessage.getEncryptedType())
        );
        return PendingDecryptItemRetryResult.requeued();
    }

    /**
     * Publishes a terminal failure event when decrypt returned a hard error.
     */
    private PendingDecryptItemRetryResult handleRetryFailed(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType,
            DecryptAttemptResult decryptResult
    ) {
        retryEventPublisher.publishRetryFailed(originalItem, pendingMessage, encType, decryptResult.getError());
        log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                originalItem.getUeId(),
                pendingMessage.getMsgId(),
                encType,
                decryptResult.getError());
        return PendingDecryptItemRetryResult.failure();
    }

    /**
     * Treats skip as a completed branch because the item is not kept in the waiting queue anymore.
     */
    private PendingDecryptItemRetryResult handleRetrySkip(
            PendingDecryptItem originalItem,
            SignalingMessage pendingMessage,
            String encType
    ) {
        retryEventPublisher.publishRetrySkip(originalItem, pendingMessage, encType);
        return PendingDecryptItemRetryResult.success();
    }
}
