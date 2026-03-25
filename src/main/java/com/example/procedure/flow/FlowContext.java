package com.example.procedure.flow;

import com.example.procedure.processing.procedure.ProcedureStateService;
import com.example.procedure.processing.procedure.flow.ProcedureClosePolicy;

public record FlowContext(
        ProcedureStateService procedureStateService,
        // REFACTOR STEP: RULE_FLOW_BOUNDARY
        ProcedureClosePolicy closeDecider
) {}
