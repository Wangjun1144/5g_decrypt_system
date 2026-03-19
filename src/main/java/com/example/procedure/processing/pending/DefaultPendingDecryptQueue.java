package com.example.procedure.processing.pending;

import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 待解密消息队列的默认实现。
 *
 * 当前阶段定位：
 * - 它是新的 pending 解密队列入口实现
 * - 内部继续复用现有 PendingMessageRepository
 * - 不改变当前队列行为，只收口入口边界
 *
 * 为什么这一层值得单独抽：
 * - 现在 pending 逻辑已经开始成为主链中的独立阶段能力
 * - 继续把它绑定在旧 service 包下，会限制后续架构演进
 * - 先把接口收口，后续才好无痛替换为 Redis / MQ / Kafka 实现
 *
 * 当前实现仍然保持：
 * - 按 ueId 存取
 * - 使用 PendingMessageRecord 作为新阶段内部记录对象
 * - 不引入新的重试策略变化
 */
@Service
public class DefaultPendingDecryptQueue implements PendingDecryptQueue {

    private final PendingMessageRepository repository;

    public DefaultPendingDecryptQueue(PendingMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(String ueId, SignalingMessage msg, DecryptAttemptResult.WaitReason reason) {
        repository.enqueue(
                ueId,
                new PendingMessageRecord(
                        System.currentTimeMillis(),
                        safeMsgId(msg),
                        reason,
                        msg
                )
        );
    }

    @Override
    public List<PendingMessageRecord> pollBatch(String ueId, int max) {
        return repository.pollBatch(ueId, max);
    }

    @Override
    public void requeue(String ueId, PendingMessageRecord record) {
        repository.requeue(ueId, record);
    }

    @Override
    public int size(String ueId) {
        return repository.size(ueId);
    }

    private String safeMsgId(SignalingMessage msg) {
        try {
            return msg.getMsgId();
        } catch (Exception e) {
            return "UNKNOWN_MSG";
        }
    }
}
