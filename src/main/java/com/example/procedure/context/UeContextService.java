package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * UE 上下文服务。
 *
 * 当前职责：
 * 1. 加载 UEContext
 * 2. 创建默认上下文
 * 3. 调度 updater 体系执行上下文更新
 * 4. 落库存储
 *
 * 设计说明：
 * - Redis 细节下沉到 UeContextRepository
 * - 本类保留“业务级上下文管理”的职责
 * - 这是新的正式服务边界，供新主链直接依赖
 */
@Service
public class UeContextService {

    private final UeContextRepository ueContextRepository;
    private final UeContextUpdateDispatcher updateDispatcher;
    private final UeContextUpdateSupport updateSupport;

    public UeContextService(
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
