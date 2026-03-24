package com.example.procedure.service;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.processing.procedure.ProcedureStateStoreService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @deprecated 旧的流程状态管理兼容层。
 *
 * 当前阶段保留这个类的原因：
 * - 旧代码和旧测试可能仍然依赖 ProManager_Service
 * - 新主链已经迁移到 processing.procedure 相关正式边界
 * - 为避免一次性修改所有旧调用方，这里保留旧类名作为兼容门面
 *
 * 后续建议：
 * - 新代码不要继续依赖 ProManager_Service
 * - 本类最终可迁入 legacy 包或删除
 */
@Deprecated
@Service
public class ProManager_Service {

    private final ProcedureStateStoreService delegate;

    public ProManager_Service(ProcedureStateStoreService delegate) {
        this.delegate = delegate;
    }

    public Map<String, Object> add_ActProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        return delegate.createActiveProcedure(ueId, typeEnum, msgType);
    }

    public Map<String, Object> get_ActProcedures(String ueId) {
        return delegate.getActiveProcedures(ueId);
    }

    public List<Procedure> listActiveProcedures(String ueId) {
        return delegate.listActiveProcedures(ueId);
    }

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

    public Map<String, Object> end_Procedure(String ueId, String procedureId) {
        return delegate.endProcedure(ueId, procedureId);
    }
}
