package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * Typed procedure-state entry used by the procedure domain itself.
 */
@Service
public class ProcedureStateService {
    // REFACTOR STEP: PROCEDURE_STATE_SUBPACKAGE_REORG

    private final ProcedureStateRepository repository;
    private final ProcedureLifecycleService lifecycleService;

    public ProcedureStateService(
            ProcedureStateRepository repository,
            ProcedureLifecycleService lifecycleService
    ) {
        this.repository = repository;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Lists active procedures in domain-object form for the current UE.
     */
    public List<Procedure> listActiveProcedures(String ueId) {
        return repository.listActiveProcedures(ueId);
    }

    /**
     * Returns the typed active-procedure query view for callers that need count plus data together.
     */
    public ActiveProceduresView getActiveProcedures(String ueId) {
        return repository.getActiveProcedures(ueId);
    }

    /**
     * Creates one active procedure and normalizes the compatibility result into a typed outcome.
     */
    public ProcedureStateOperationResult createProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        return repository.createActiveProcedure(ueId, typeEnum, msgType);
    }

    /**
     * Updates the core phase/order markers for one active procedure.
     */
    public ProcedureStateOperationResult updateProcedure(
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
     * Updates extended close-policy state for one active procedure.
     */
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
     * Ends one active procedure and converts the result into the typed operation contract.
     */
    public ProcedureStateOperationResult endProcedure(String ueId, String procedureId) {
        return lifecycleService.endProcedure(ueId, procedureId);
    }
}
