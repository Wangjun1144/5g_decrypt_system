package com.example.procedure.context;

import com.example.procedure.model.UEContext;

/**
 * UE 上下文仓储接口。
 *
 * 职责：
 * - 屏蔽 Redis 读写细节
 * - 让 UEContextService 只保留“业务级上下文服务”的职责
 *
 * 当前阶段：
 * - 先支持 get / save
 * - 其他映射表能力（amf/ran/crnti）后续再逐步补齐
 */
public interface UeContextRepository {

    /**
     * 按 ueId 读取上下文。
     */
    UEContext findByUeId(String ueId);

    /**
     * 保存上下文。
     */
    void save(UEContext context);
}