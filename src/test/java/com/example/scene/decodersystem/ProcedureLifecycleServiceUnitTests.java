package com.example.scene.decodersystem;

import com.example.procedure.model.Procedure;
import com.example.procedure.processing.procedure.state.ProcedureArchiveWriter;
import com.example.procedure.processing.procedure.state.ProcedureLifecycleService;
import com.example.procedure.processing.procedure.state.ProcedureStateOperationResult;
import com.example.procedure.processing.procedure.state.ProcedureStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcedureLifecycleServiceUnitTests {

    @Mock
    private ProcedureStateRepository repository;

    @Mock
    private ProcedureArchiveWriter archiveWriter;

    private ProcedureLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lifecycleService = new ProcedureLifecycleService(repository, archiveWriter);
    }

    @Test
    @DisplayName("endProcedure archives then deletes active state")
    void endProcedureShouldArchiveThenDelete() throws Exception {
        Procedure procedure = new Procedure();
        procedure.setProcedureId("p-1");
        procedure.setUeId("ue-1");

        when(repository.findProcedure("p-1")).thenReturn(procedure);

        ProcedureStateOperationResult result = lifecycleService.endProcedure("ue-1", "p-1");

        assertTrue(result.isSuccess());
        assertEquals("p-1", result.getProcedureId());
        assertTrue(procedure.getEndTime() != null && !procedure.getEndTime().isBlank());
        verify(repository, times(1)).findProcedure("p-1");
        verify(archiveWriter, times(1)).append(procedure);
        verify(repository, times(1)).deleteProcedure("ue-1", "p-1");
    }

    @Test
    @DisplayName("endProcedure returns failure when procedure is missing")
    void endProcedureShouldFailWhenProcedureMissing() throws Exception {
        when(repository.findProcedure("missing")).thenReturn(null);

        ProcedureStateOperationResult result = lifecycleService.endProcedure("ue-1", "missing");

        assertTrue(!result.isSuccess());
        verify(repository, times(1)).findProcedure("missing");
        verify(archiveWriter, never()).append(any());
        verify(repository, never()).deleteProcedure("ue-1", "missing");
    }

    @Test
    @DisplayName("endProcedure returns failure when archive fails")
    void endProcedureShouldFailWhenArchiveFails() throws Exception {
        Procedure procedure = new Procedure();
        procedure.setProcedureId("p-1");
        procedure.setUeId("ue-1");

        when(repository.findProcedure("p-1")).thenReturn(procedure);
        doThrow(new IOException("archive failed")).when(archiveWriter).append(procedure);

        ProcedureStateOperationResult result = lifecycleService.endProcedure("ue-1", "p-1");

        assertTrue(!result.isSuccess());
        verify(archiveWriter, times(1)).append(procedure);
        verify(repository, never()).deleteProcedure("ue-1", "p-1");
    }
}
