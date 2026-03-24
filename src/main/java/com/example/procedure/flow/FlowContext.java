package com.example.procedure.flow;

import com.example.procedure.processing.procedure.ProcedureStateService;
import com.example.procedure.rule.ProcedureCloseDecider;

public record FlowContext(
        ProcedureStateService procedureStateService,
        ProcedureCloseDecider closeDecider
) {}
