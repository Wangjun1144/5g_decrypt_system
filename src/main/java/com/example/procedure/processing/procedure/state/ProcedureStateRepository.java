package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;

import java.util.List;

/**
 * Repository boundary for active procedure state persistence.
 */
public interface ProcedureStateRepository {

    ProcedureStateOperationResult createActiveProcedure(String ueId, ProcedureTypeEnum typeEnum, String msgType);

    List<Procedure> listActiveProcedures(String ueId);

    ActiveProceduresView getActiveProcedures(String ueId);

    ProcedureStateOperationResult updateActiveProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    );

    ProcedureStateOperationResult updateActiveProcedureEx(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex,
            boolean endSeen,
            long endSeenAtMs,
            int keyMask
    );

    Procedure findProcedure(String procedureId);

    void deleteProcedure(String ueId, String procedureId);
}
