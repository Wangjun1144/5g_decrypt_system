package com.example.procedure.processing.procedure;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程状态服务。
 *
 * 当前阶段定位：
 * - 这是 procedure 领域在新主链中的正式状态边界
 * - 负责承接“活跃流程加载 / 创建 / 更新 / 结束”等状态操作
 * - 对外提供稳定、类型化的操作接口
 *
 * 这样做的意义：
 * - 新主链不再依赖 legacy service 名称
 * - 新主链不再暴露 Map<String, Object> 这类旧返回结构
 * - 后续如果继续拆成 repository / infrastructure / 微服务边界，收口点已经明确
 */
@Service
public class ProcedureStateService {

    private final ProcedureStateStoreService storeService;

    public ProcedureStateService(ProcedureStateStoreService storeService) {
        this.storeService = storeService;
    }

    public List<Procedure> listActiveProcedures(String ueId) {
        return storeService.listActiveProcedures(ueId);
    }

    public ProcedureStateOperationResult createProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        return toOperationResult(
                storeService.createActiveProcedure(ueId, typeEnum, msgType),
                "create procedure"
        );
    }

    public ProcedureStateOperationResult updateProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    ) {
        return toOperationResult(
                storeService.updateActiveProcedure(
                        ueId,
                        procedureId,
                        msgType,
                        lastPhaseIndex,
                        lastOrderIndex
                ),
                "update procedure"
        );
    }

    public ProcedureStateOperationResult updateProcedureEx(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex,
            boolean endSeen,
            long endSeenAtMs,
            int keyMask
    ) {
        return toOperationResult(
                storeService.updateActiveProcedureEx(
                        ueId,
                        procedureId,
                        msgType,
                        lastPhaseIndex,
                        lastOrderIndex,
                        endSeen,
                        endSeenAtMs,
                        keyMask
                ),
                "update procedure"
        );
    }

    public ProcedureStateOperationResult endProcedure(String ueId, String procedureId) {
        return toOperationResult(
                storeService.endProcedure(ueId, procedureId),
                "end procedure"
        );
    }

    private ProcedureStateOperationResult toOperationResult(
            java.util.Map<String, Object> result,
            String fallbackAction
    ) {
        if (result == null) {
            return ProcedureStateOperationResult.failure(fallbackAction + " failed: null result");
        }

        Object status = result.get("status");
        boolean success = status instanceof Number && ((Number) status).intValue() == 0;

        Object procedureId = result.get("procedureId");
        Object message = result.get("msg");

        if (success) {
            return ProcedureStateOperationResult.success(
                    procedureId == null ? null : String.valueOf(procedureId),
                    message == null ? "ok" : String.valueOf(message)
            );
        }

        return ProcedureStateOperationResult.failure(
                message == null ? fallbackAction + " failed" : String.valueOf(message)
        );
    }
}
