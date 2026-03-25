package com.example.procedure.context;

import com.example.procedure.model.UEContext;

/**
 * UEContext 仓储边界。
 *
 * 当前定位：
 * 1. 这是 context 领域对外暴露的正式仓储接口
 * 2. 业务层只依赖这个接口，不依赖具体 Redis/DB 实现
 * 3. 具体实现应放在 infrastructure.context 等基础设施包下
 *
 * 这样做的意义：
 * 1. 让 UeContextService 只保留业务级上下文服务职责
 * 2. 让存储实现从 context 包中下沉
 * 3. 为后续切换 Redis/DB/缓存组合实现预留清晰扩展点
 */
public interface UeContextRepository {

    /**
     * 根据 UE ID 查找上下文。
     *
     * @param ueId UE 标识
     * @return UEContext；如果不存在则返回 null
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    UEContext findByUeId(String ueId);

    /**
     * 保存上下文。
     *
     * @param ctx 当前上下文
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    void save(UEContext ctx);
}
