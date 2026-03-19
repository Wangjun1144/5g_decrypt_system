package com.example.procedure.processing.pending;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;

import java.util.List;

/**
 * “待解密消息队列”的新入口接口。
 *
 * 设计目标：
 * 1. 把“等待后续 key / 算法 / 上下文补全后再重试的消息”抽象成独立能力边界。
 * 2. 让主链不再直接依赖旧的 PendingMessageService。
 * 3. 为后续演进到 Redis 队列、Kafka waiting topic、retry topic、dead-letter topic 做准备。
 *
 * 当前阶段语义：
 * - enqueue: 当前消息暂时无法解密，进入等待队列
 * - pollBatch: 拉取一批待重试消息
 * - requeue: 当前轮仍无法处理，重新入队
 * - size: 查询某个 UE 维度下的等待数量
 *
 * 说明：
 * - 当前接口仍然保持“按 ueId 分桶”的语义，
 *   因为这和当前系统的上下文读取及重试方式保持一致。
 * - 后续如果要走流式分布式架构，可以进一步演化为按 key 分区、按 topic 分发。
 */
public interface PendingDecryptQueue {

    void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason);

    List<PendingMessageRecord> pollBatch(String ueId, int max);

    void requeue(String ueId, PendingMessageRecord record);

    int size(String ueId);
}
