package com.example.procedure.context;

import com.example.procedure.infrastructure.context.RedisUeContextStore;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Repository;

/**
 * @deprecated 旧的 Redis UEContext 仓储命名兼容层。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖 context.RedisUeContextRepository 这个名字
 * 2. 新的正式实现已经迁到 infrastructure.context.RedisUeContextStore
 * 3. 这里收缩为兼容壳，避免旧引用立即失效
 */
@Deprecated
@Repository
public class RedisUeContextRepository implements UeContextRepository {

    /**
     * 正式 Redis 存储实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final RedisUeContextStore delegate;

    /**
     * 构造兼容层。
     *
     * 这里直接依赖正式实现类，
     * 避免继续以 UeContextRepository 接口类型形成 Bean 候选歧义。
     *
     * @param delegate 正式 Redis 存储实现
     */
    public RedisUeContextRepository(RedisUeContextStore delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧接口：按 UE ID 查找上下文。
     *
     * @param ueId UE 标识
     * @return UEContext
     */
    @Override
    public UEContext findByUeId(String ueId) {
        return delegate.findByUeId(ueId);
    }

    /**
     * 兼容旧接口：保存上下文。
     *
     * @param ctx 当前上下文
     */
    @Override
    public void save(UEContext ctx) {
        delegate.save(ctx);
    }
}
