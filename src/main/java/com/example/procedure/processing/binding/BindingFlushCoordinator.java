package com.example.procedure.processing.binding;

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

    private final PendingBindingStore pendingBindingStore;

    public BindingFlushCoordinator(PendingBindingStore pendingBindingStore) {
        this.pendingBindingStore = pendingBindingStore;
    }

    public List<SignalingMessage> flushByNgap(String ngapId, String ueId) {
        return pendingBindingStore.releaseNgapPending(ngapId, ueId);
    }

    public List<SignalingMessage> flushByRnti(String rntiType, String ueId) {
        return pendingBindingStore.releaseRntiPending(rntiType, ueId);
    }

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