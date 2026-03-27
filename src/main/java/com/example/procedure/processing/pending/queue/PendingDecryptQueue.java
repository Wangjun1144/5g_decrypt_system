package com.example.procedure.processing.pending.queue;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.pending.queue.PendingDecryptItem;

import java.util.List;

/**
 * 寰呰В瀵嗘秷鎭槦鍒楄竟鐣屻€?
 *
 * 褰撳墠闃舵瀹氫綅锛?
 * - 杩欐槸 pending decrypt 鐨勬寮忚闂竟鐣?
 * - 姝ｅ紡妯″瀷涓?PendingDecryptItem
 * - 鍏煎鏃ц皟鐢ㄦ椂锛屼粛淇濈暀渚挎嵎 enqueue 鏂瑰紡
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
