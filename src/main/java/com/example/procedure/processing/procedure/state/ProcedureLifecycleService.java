package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles lifecycle transitions that end an active procedure.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcedureLifecycleService {

    /**
     * Reuses the existing timestamp format so archived data keeps a stable shape.
     */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProcedureStateRepository repository;
    private final ProcedureArchiveWriter archiveWriter;

    /**
     * Ends one active procedure by stamping end time, archiving it, and then removing live state.
     */
    public ProcedureStateOperationResult endProcedure(String ueId, String procedureId) {
        // We resolve from repository first so callers get a stable typed result instead of a null-driven contract.
        Procedure procedure = repository.findProcedure(procedureId);
        if (procedure == null) {
            return ProcedureStateOperationResult.failure("procedure not found");
        }

        // The archive record should reflect the exact moment the procedure left active state.
        procedure.setEndTime(LocalDateTime.now().format(FORMATTER));

        try {
            archiveWriter.append(procedure);
        } catch (IOException e) {
            // Archive failure must short-circuit deletion; otherwise we'd lose the finished procedure record.
            log.error("Failed to archive procedure. ueId={}, procedureId={}", ueId, procedureId, e);
            return ProcedureStateOperationResult.failure(e.getMessage());
        }

        // Only remove the active copy after archive succeeds.
        repository.deleteProcedure(ueId, procedureId);

        return ProcedureStateOperationResult.success(procedureId, "archived");
    }
}
