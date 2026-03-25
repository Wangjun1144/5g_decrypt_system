package com.example.procedure.processing.binding;

import com.example.procedure.infrastructure.binding.RedisBindingStateStore;
import org.springframework.stereotype.Component;

/**
 * @deprecated 旧的 binding 状态存储门面。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖 processing.binding.BindingStateStore 这个名字
 * 2. 新的正式实现已经迁到 infrastructure.binding.RedisBindingStateStore
 * 3. 这里收缩为兼容壳，避免旧引用立即失效
 */
@Deprecated
@Component
public class BindingStateStore {

    /**
     * 正式 Redis 绑定状态实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_BINDING
    private final RedisBindingStateStore delegate;

    /**
     * 构造旧兼容层。
     *
     * @param delegate 正式 Redis 绑定状态实现
     */
    public BindingStateStore(RedisBindingStateStore delegate) {
        this.delegate = delegate;
    }

    public String lookupUeIdByNgapId(String ngapId) {
        return delegate.lookupUeIdByNgapId(ngapId);
    }

    public String lookupUeIdByRntiType(String rntiType) {
        return delegate.lookupUeIdByRntiType(rntiType);
    }

    public boolean isUeNgapUnbound(String ueId) {
        return delegate.isUeNgapUnbound(ueId);
    }

    public boolean isUeRntiUnbound(String ueId) {
        return delegate.isUeRntiUnbound(ueId);
    }

    public boolean isNgapUnbound(String ngapId) {
        return delegate.isNgapUnbound(ngapId);
    }

    public boolean isRntiTypeUnbound(String rntiType) {
        return delegate.isRntiTypeUnbound(rntiType);
    }

    public void bindNgapIdToUe(String ngapId, String ueId) {
        delegate.bindNgapIdToUe(ngapId, ueId);
    }

    public void bindRntiTypeToUe(String rntiType, String ueId) {
        delegate.bindRntiTypeToUe(rntiType, ueId);
    }
}
