package com.example.procedure.context;

import com.example.procedure.infrastructure.context.RedisUeContextStore;
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
 * - Redis 细节下沉到基础设施实现
 * - 本类保留“业务级上下文管理”的职责
 * - 这是新的正式服务边界，供新主链直接依赖
 */
@Service
public class UeContextService {

    /**
     * UEContext 正式存储实现。
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final RedisUeContextStore ueContextRepository;

    /**
     * 上下文更新分发器。
     */
    private final UeContextUpdateDispatcher updateDispatcher;

    /**
     * 上下文更新辅助组件。
     */
    private final UeContextUpdateSupport updateSupport;

    /**
     * 上下文更新事件发布器。
     */
    private final UeContextUpdatedEventPublisher eventPublisher;

    /**
     * 构造 UE 上下文服务。
     *
     * 这里直接依赖正式 Redis 实现类，
     * 避免和兼容层 RedisUeContextRepository 形成 Spring 注入歧义。
     *
     * @param ueContextRepository UEContext 正式存储实现
     * @param updateDispatcher 更新分发器
     * @param updateSupport 更新辅助组件
     * @param eventPublisher 上下文更新事件发布器
     */
    public UeContextService(
            RedisUeContextStore ueContextRepository,
            UeContextUpdateDispatcher updateDispatcher,
            UeContextUpdateSupport updateSupport,
            UeContextUpdatedEventPublisher eventPublisher
    ) {
        this.ueContextRepository = ueContextRepository;
        this.updateDispatcher = updateDispatcher;
        this.updateSupport = updateSupport;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 加载某个 UE 的上下文。
     *
     * @param ueId UE 标识
     * @return UEContext；如果不存在则返回 null
     */
    public UEContext getContext(String ueId) {
        return ueContextRepository.findByUeId(ueId);
    }

    /**
     * 保存上下文。
     *
     * @param ctx 当前上下文
     */
    public void saveContext(UEContext ctx) {
        ueContextRepository.save(ctx);
    }

    /**
     * 若不存在则创建默认上下文。
     *
     * @param ueId UE 标识
     * @return 已存在或新建的上下文
     */
    public UEContext getOrCreate(String ueId) {
        UEContext ctx = getContext(ueId);
        if (ctx == null) {
            ctx = createDefaultContext(ueId);
        }
        return ctx;
    }

    /**
     * 正式入口：根据请求对象更新 UE 上下文。
     *
     * @param request 上下文更新请求
     * @return 上下文更新结果
     */
    public UeContextUpdateResult process(UeContextUpdateRequest request) {
        SignalingMessage msg = request.getMessage();
        String ueId = msg == null ? null : msg.getUeId();

        if (ueId == null || ueId.isEmpty()) {
            UeContextUpdateResult result = UeContextUpdateResult.skipped(
                    null,
                    request.getProcedureId(),
                    "skip ue context update: ueId is empty"
            );

            publishContextEvent(request, result, "ue-context-update-skipped");
            return result;
        }

        UEContext existing = getContext(ueId);
        boolean created = existing == null;

        UEContext ctx = created ? createDefaultContext(ueId) : existing;

        updateDispatcher.dispatch(msg, ctx, request.getProcedureId(), updateSupport);
        saveContext(ctx);

        UeContextUpdateResult result = UeContextUpdateResult.updated(
                ueId,
                created,
                request.getProcedureId(),
                created ? "ue context created and updated" : "ue context updated"
        );

        publishContextEvent(request, result, "ue-context-updated");
        return result;
    }

    /**
     * 兼容旧入口：根据当前消息和流程标识更新 UE 上下文。
     *
     * @param msg 当前消息
     * @param procedureId 当前流程 ID
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        process(new UeContextUpdateRequest(
                msg,
                procedureId,
                null,
                null,
                null,
                false
        ));
    }

    /**
     * 创建默认上下文。
     *
     * @param ueId UE 标识
     * @return 新建的默认上下文
     */
    private UEContext createDefaultContext(String ueId) {
        UEContext ctx = new UEContext();
        ctx.setUeId(ueId);
        ctx.setAttachState("INIT");
        return ctx;
    }

    /**
     * 发布一条 UEContext 更新事件。
     *
     * @param request 当前更新请求
     * @param result 当前更新结果
     * @param action 当前动作名
     */
    private void publishContextEvent(
            UeContextUpdateRequest request,
            UeContextUpdateResult result,
            String action
    ) {
        SignalingMessage msg = request.getMessage();

        UeContextUpdatedEvent event = new UeContextUpdatedEvent(
                action,
                request.getCorrelationId(),
                result.getUeId(),
                result.getProcedureId(),
                msg == null ? null : msg.getMsgId(),
                msg == null ? null : msg.getMsgType(),
                msg == null ? null : msg.getFrameNo(),
                msg == null ? null : msg.getTimestamp(),
                request.getSourceType(),
                request.getSourceName(),
                request.isReentry(),
                result.isCreated(),
                result.isUpdated(),
                result.getMessage()
        );

        eventPublisher.publish(event);
    }
}
