package com.example.procedure.service;

import com.example.procedure.context.UeContextRepository;
import com.example.procedure.context.UeContextUpdateDispatcher;
import com.example.procedure.context.UeContextUpdateSupport;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * UE 上下文服务。
 *
 * 当前阶段职责：
 * 1. 加载 UEContext
 * 2. 创建默认上下文
 * 3. 调度 updater 体系执行上下文更新
 * 4. 落库存储
 *
 * 设计说明：
 * - Redis 细节已下沉到 UeContextRepository
 * - 本类保留“业务级上下文管理”职责
 * - 这与文档中“收口 UEContextService 边界”的目标一致。
 */
@Service
public class UEContextService {

    private final UeContextRepository ueContextRepository;
    private final UeContextUpdateDispatcher updateDispatcher;
    private final UeContextUpdateSupport updateSupport;

    public UEContextService(
            UeContextRepository ueContextRepository,
            UeContextUpdateDispatcher updateDispatcher,
            UeContextUpdateSupport updateSupport
    ) {
        this.ueContextRepository = ueContextRepository;
        this.updateDispatcher = updateDispatcher;
        this.updateSupport = updateSupport;
    }

    /**
     * 加载某个 UE 的上下文。
     */
    public UEContext getContext(String ueId) {
        return ueContextRepository.findByUeId(ueId);
    }

    /**
     * 保存上下文。
     */
    public void saveContext(UEContext ctx) {
        ueContextRepository.save(ctx);
    }

    /**
     * 若不存在则创建默认上下文。
     */
    public UEContext getOrCreate(String ueId) {
        UEContext ctx = getContext(ueId);
        if (ctx == null) {
            ctx = new UEContext();
            ctx.setUeId(ueId);
            ctx.setAttachState("INIT");
        }
        return ctx;
    }

    /**
     * 根据当前消息和流程标识，更新 UE 上下文。
     *
     * 说明：
     * - 具体“每类消息如何更新上下文”的规则仍由 updater 体系负责
     * - 本类只负责加载、调度、保存
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        String ueId = msg.getUeId();
        if (ueId == null || ueId.isEmpty()) {
            return;
        }

        UEContext ctx = getOrCreate(ueId);

        updateDispatcher.dispatch(msg, ctx, procedureId, updateSupport);

        saveContext(ctx);
    }
}