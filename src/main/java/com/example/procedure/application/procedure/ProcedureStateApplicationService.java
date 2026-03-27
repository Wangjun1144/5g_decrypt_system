package com.example.procedure.application.procedure;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.processing.procedure.state.ActiveProceduresView;
import com.example.procedure.processing.procedure.state.ProcedureLifecycleService;
import com.example.procedure.processing.procedure.state.ProcedureStateOperationResult;
import com.example.procedure.processing.procedure.state.ProcedureStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Formal application-layer entry for procedure state operations.
 *
 * New code should depend on this service instead of older store-style entry points.
 */
@Service
@RequiredArgsConstructor
public class ProcedureStateApplicationService {

    private final ProcedureStateRepository repository;
    private final ProcedureLifecycleService lifecycleService;

    /**
     * Creates one active procedure and converts the repository response into the formal typed result.
     */
    public ProcedureStateOperationResult createActiveProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        return repository.createActiveProcedure(ueId, typeEnum, msgType);
    }

    /**
     * Lists active procedures for one UE in domain-object form.
     */
    public List<Procedure> listActiveProcedures(String ueId) {
        return repository.listActiveProcedures(ueId);
    }

    /**
     * Reads active procedures using the formal typed query view.
     */
    public ActiveProceduresView getActiveProcedures(String ueId) {
        return repository.getActiveProcedures(ueId);
    }

    /**
     * Updates the active procedure progress markers tracked by the live-state repository.
     */
    public ProcedureStateOperationResult updateActiveProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    ) {
        return repository.updateActiveProcedure(
                ueId,
                procedureId,
                msgType,
                lastPhaseIndex,
                lastOrderIndex
        );
    }

    /**
     * Updates the extended active procedure state used by close-policy and key-mask logic.
     */
    public ProcedureStateOperationResult updateActiveProcedureEx(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex,
            boolean endSeen,
            long endSeenAtMs,
            int keyMask
    ) {
        return repository.updateActiveProcedureEx(
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
     * Ends one active procedure through the dedicated lifecycle service.
     */
    public ProcedureStateOperationResult endProcedure(String ueId, String procedureId) {
        return lifecycleService.endProcedure(ueId, procedureId);
    }
}
