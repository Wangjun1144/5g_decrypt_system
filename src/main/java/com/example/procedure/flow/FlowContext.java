package com.example.procedure.flow;

import com.example.procedure.rule.ProcedureCloseDecider;
import com.example.procedure.service.ProManager_Service;

public record FlowContext(
        ProManager_Service proManagerService,
        ProcedureCloseDecider closeDecider
) {}
