package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.service.PendingMessageService;
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
 * 1. 当上下文中 key / 算法变得可用时，批量重试该 UE 的待解密消息
 * 2. 对 WAITING / FAILED / OK / SKIP 做统一处理
 *
 * 阶段 1 目标：
 * - 把 MsgProcessing_Service 中的“待重试队列处理”独立出来
 * - 保持当前内存队列行为不变
 *
 * 阶段 2 可继续演进为：
 * - PendingMessageRepository
 * - Redis / MQ / Kafka waiting topic
 * - 重试次数、退避和死信策略
 */
@Service
public class PendingRetryService {

    private static final Logger log = LoggerFactory.getLogger(PendingRetryService.class);

    private static final int MAX_DECRYPT_DEPTH = 4;

    private final PendingMessageService pendingMessageService;
    private final DecryptCoordinator decryptCoordinator;

    /**
     * 这里直接依赖 MessageProcessor，是为了在重试成功后继续走完整主链路。
     *
     * 说明：
     * - 这是当前单体阶段下最简单、最稳定的接法
     * - 后续如果你进入阶段 2/3，可以改成内部事件或回调接口
     */
    private final MessageProcessor messageProcessor;

    public PendingRetryService(
            PendingMessageService pendingMessageService,
            DecryptCoordinator decryptCoordinator,
            MessageProcessor messageProcessor
    ) {
        this.pendingMessageService = pendingMessageService;
        this.decryptCoordinator = decryptCoordinator;
        this.messageProcessor = messageProcessor;
    }

    /**
     * 在当前消息处理完成、上下文可能已更新之后，
     * 尝试重试同一 UE 下此前 pending 的加密消息。
     */
    public void retryPendingDecrypt(String ueId, UEContext context) {
        if (ueId == null || ueId.isEmpty()) {
            return;
        }

        boolean canTryNas = nasKeyReady(context);
        boolean canTryRrc = rrcKeyReady(context);

        // 当前没有任何可用于解密的 key，就不要白跑
        if (!canTryNas && !canTryRrc) {
            return;
        }

        int batchSize = 200;
        List<PendingMessageService.PendingItem> items = pendingMessageService.pollBatch(ueId, batchSize);
        if (items.isEmpty()) {
            return;
        }

        int ok = 0;
        int requeue = 0;
        int fail = 0;

        for (PendingMessageService.PendingItem item : items) {
            SignalingMessage pendingMessage = item.msg;
            if (pendingMessage == null) {
                continue;
            }

            String encType = normalizeEncType(pendingMessage.getEncryptedType());

            // 若当前上下文仍不具备该类消息的解密条件，则直接回队
            if ("NAS".equals(encType) && !canTryNas) {
                pendingMessageService.requeue(ueId, item);
                requeue++;
                continue;
            }

            if ("PDCP".equals(encType) && !canTryRrc) {
                pendingMessageService.requeue(ueId, item);
                requeue++;
                continue;
            }

            if ("NAS+PDCP".equals(encType) && (!canTryNas && !canTryRrc)) {
                pendingMessageService.requeue(ueId, item);
                requeue++;
                continue;
            }

            DecryptAttemptResult decryptResult =
                    decryptCoordinator.tryDecryptByType(pendingMessage, encType, context);

            if (decryptResult.getStatus() == DecryptAttemptResult.Status.OK) {
                if (safeDecryptDepth(pendingMessage) >= MAX_DECRYPT_DEPTH) {
                    log.warn("Pending decrypt max-depth reached(drop): ueId={}, msgId={}, encType={}, depth={}",
                            ueId, pendingMessage.getMsgId(), pendingMessage.getEncryptedType(), safeDecryptDepth(pendingMessage));
                    continue;
                }

                ok++;

                try {
                    decryptCoordinator.handleDecryptSuccess(new MessageProcessingContext(pendingMessage));

                    // 无论回流后内部是否仍加密，都重新走一遍完整主链路
                    // 这样可以保持与原逻辑一致：分类 / 流程识别 / 分发 / 上下文更新 都不会被跳过
                    messageProcessor.process(pendingMessage);
                } catch (Exception e) {
                    log.error("Pending decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                            pendingMessage.getUeId(), pendingMessage.getMsgId(), encType, e.getMessage(), e);
                }

                log.info("Pending decrypt OK: ueId={}, msgId={}, encType={}",
                        ueId, pendingMessage.getMsgId(), encType);

                // 保留原调试输出，便于和旧版行为比对
                SignalingDumpWriter.write(
                        pendingMessage,
                        Paths.get("logs/signaling_dump_1.log"),
                        true
                );
                continue;
            }

            if (decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
                // 仍缺材料，回队尾等待下一轮
                PendingMessageService.PendingItem newItem =
                        new PendingMessageService.PendingItem(
                                System.currentTimeMillis(),
                                item.msgId,
                                decryptResult.getReason(),
                                pendingMessage
                        );
                pendingMessageService.requeue(ueId, newItem);
                requeue++;
                continue;
            }

            if (decryptResult.getStatus() == DecryptAttemptResult.Status.FAILED) {
                fail++;
                log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                        ueId, pendingMessage.getMsgId(), encType, decryptResult.getError());
                continue;
            }

            // SKIP：表示当前不需要解密，可直接视为成功离队
            ok++;
        }

        log.info("Pending decrypt retry done: ueId={}, batch={}, ok={}, requeue={}, fail={}, remain={}",
                ueId, items.size(), ok, requeue, fail, pendingMessageService.size(ueId));
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
}