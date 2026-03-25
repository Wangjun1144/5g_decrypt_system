package com.example.procedure.legacy.service;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.processing.procedure.ProcedureStateStoreService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 旧流程状态管理兼容 facade。
 *
 * 当前用途：
 * 1. 承接旧 ProManager_Service 的兼容职责
 * 2. 把旧 service 命名和新的 procedure state 边界隔开
 * 3. 为后续继续清理旧 service 包做准备
 */
@Service
public class LegacyProcedureStateFacade {

    /**
     * 正式流程状态存储服务。
     */
    // REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
    private final ProcedureStateStoreService delegate;

    /**
     * 构造旧流程状态兼容 facade。
     *
     * @param delegate 正式流程状态存储服务
     */
    public LegacyProcedureStateFacade(ProcedureStateStoreService delegate) {
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
        return delegate.createActiveProcedure(ueId, typeEnum, msgType);
    }

    /**
     * 兼容旧入口：获取活跃流程集合。
     *
     * @param ueId UE 标识
     * @return 旧风格返回结果
     */
    public Map<String, Object> get_ActProcedures(String ueId) {
        return delegate.getActiveProcedures(ueId);
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
        return delegate.updateActiveProcedure(
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
        return delegate.updateActiveProcedureEx(
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
        return delegate.endProcedure(ueId, procedureId);
    }
}
