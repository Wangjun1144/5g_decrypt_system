package com.example.procedure.service;

import com.example.procedure.legacy.service.LegacyProcedureStateFacade;
import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @deprecated 旧的流程状态管理兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 ProManager_Service
 * - 新主链已经迁移到 processing.procedure 相关正式边界
 * - 当前兼容逻辑已经进一步收口到 legacy.service 包
 *
 * 后续建议：
 * - 新代码不要继续依赖 ProManager_Service
 * - 本类最终可迁入真正的 legacy 包或删除
 */
@Deprecated
@Service
public class ProManager_Service {

    /**
     * 旧流程状态兼容 facade。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final LegacyProcedureStateFacade delegate;

    /**
     * 构造旧门面类。
     *
     * @param delegate 旧流程状态兼容 facade
     */
    public ProManager_Service(LegacyProcedureStateFacade delegate) {
        this.delegate = delegate;
    }

    /**
     * 兼容旧入口：新增活跃流程。
     *
     * @param ueId UE 标识
     * @param typeEnum 流程类型
     * @param msgType 消息类型
     * @return 旧风格返回结果
     */
    public Map<String, Object> add_ActProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        return delegate.add_ActProcedure(ueId, typeEnum, msgType);
    }

    /**
     * 兼容旧入口：获取活跃流程集合。
     *
     * @param ueId UE 标识
     * @return 旧风格返回结果
     */
    public Map<String, Object> get_ActProcedures(String ueId) {
        return delegate.get_ActProcedures(ueId);
    }

    /**
     * 兼容旧入口：列出活跃流程。
     *
     * @param ueId UE 标识
     * @return 活跃流程列表
     */
    public List<Procedure> listActiveProcedures(String ueId) {
        return delegate.listActiveProcedures(ueId);
    }

    /**
     * 兼容旧入口：更新活跃流程基础字段。
     *
     * @param ueId UE 标识
     * @param procedureId 流程 ID
     * @param msgType 消息类型
     * @param lastPhaseIndex 最新阶段索引
     * @param lastOrderIndex 最新顺序索引
     * @return 旧风格返回结果
     */
    public Map<String, Object> update_ActProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    ) {
        return delegate.update_ActProcedure(
                ueId,
                procedureId,
                msgType,
                lastPhaseIndex,
                lastOrderIndex
        );
    }

    /**
     * 兼容旧入口：更新活跃流程扩展字段。
     *
     * @param ueId UE 标识
     * @param procedureId 流程 ID
     * @param msgType 消息类型
     * @param lastPhaseIndex 最新阶段索引
     * @param lastOrderIndex 最新顺序索引
     * @param endSeen 是否已看到结束信号
     * @param endSeenAtMs 结束信号时间戳
     * @param keyMask 当前 keyMask
     * @return 旧风格返回结果
     */
    public Map<String, Object> update_ActProcedureEx(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex,
            boolean endSeen,
            long endSeenAtMs,
            int keyMask
    ) {
        return delegate.update_ActProcedureEx(
                ueId,
                procedureId,
                msgType,
                lastPhaseIndex,
                lastOrderIndex,
                endSeen,
                endSeenAtMs,
                keyMask
        );
    }

    /**
     * 兼容旧入口：结束流程。
     *
     * @param ueId UE 标识
     * @param procedureId 流程 ID
     * @return 旧风格返回结果
     */
    public Map<String, Object> end_Procedure(String ueId, String procedureId) {
        return delegate.end_Procedure(ueId, procedureId);
    }
}
