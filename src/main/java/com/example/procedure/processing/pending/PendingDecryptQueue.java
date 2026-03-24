package com.example.procedure.processing.pending;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;

import java.util.List;

/**
 * 待解密消息队列边界。
 *
 * 当前阶段定位：
 * - 这是 pending decrypt 的正式访问边界
 * - 正式模型为 PendingDecryptItem
 * - 兼容旧调用时，仍保留便捷 enqueue 方式
 */
public interface PendingDecryptQueue {

    void enqueue(PendingDecryptItem item);

    List<PendingDecryptItem> pollBatch(String ueId, int max);

    void requeue(PendingDecryptItem item);

    int size(String ueId);

    default void enqueue(
            String ueId,
            SignalingMessage msg,
            DecryptAttemptResult.WaitReason reason
    ) {
        enqueue(PendingDecryptItem.of(ueId, msg, reason));
    }
}
