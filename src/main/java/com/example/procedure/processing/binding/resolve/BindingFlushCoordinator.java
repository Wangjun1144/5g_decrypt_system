package com.example.procedure.processing.binding.resolve;

import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 待绑定消息释放协调器。
 *
 * 职责：
 * 1. 在某个索引成功绑定到 ueId 后，释放相应 pending 消息
 * 2. 保持原系统“先释放历史 pending，再处理当前消息”的顺序语义
 */
@Component
public class BindingFlushCoordinator {
    // REFACTOR STEP: BINDING_SUBPACKAGE_REORG

    /**
     * 正式待绑定缓冲状态实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    private final PendingBindingStore pendingBindingStore;

    /**
     * 构造释放协调器。
     *
     * @param pendingBindingStore 正式待绑定缓冲状态实现
     */
    public BindingFlushCoordinator(PendingBindingStore pendingBindingStore) {
        this.pendingBindingStore = pendingBindingStore;
    }

    /**
     * 按 ngapId 释放历史 pending 消息。
     *
     * @param ngapId ngapId
     * @param ueId ueId
     * @return 释放后的消息列表
     */
    public List<SignalingMessage> flushByNgap(String ngapId, String ueId) {
        return pendingBindingStore.releaseNgapPending(ngapId, ueId);
    }

    /**
     * 按 rntiType 释放历史 pending 消息。
     *
     * @param rntiType rntiType
     * @param ueId ueId
     * @return 释放后的消息列表
     */
    public List<SignalingMessage> flushByRnti(String rntiType, String ueId) {
        return pendingBindingStore.releaseRntiPending(rntiType, ueId);
    }

    /**
     * 合并两路释放结果。
     *
     * @param releasedByNgap 按 ngap 释放的消息
     * @param releasedByRnti 按 rnti 释放的消息
     * @return 合并后的消息列表
     */
    public List<SignalingMessage> combineReleased(
            List<SignalingMessage> releasedByNgap,
            List<SignalingMessage> releasedByRnti
    ) {
        List<SignalingMessage> result = new ArrayList<>();
        if (releasedByNgap != null && !releasedByNgap.isEmpty()) {
            result.addAll(releasedByNgap);
        }
        if (releasedByRnti != null && !releasedByRnti.isEmpty()) {
            result.addAll(releasedByRnti);
        }
        return result;
    }
}
