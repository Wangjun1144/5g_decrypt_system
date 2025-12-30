package com.example.procedure.util;

import com.example.procedure.model.Procedure;

public final class ProcedureProgressUtil {

    private ProcedureProgressUtil(){}

    public static void advanceMonotonic(Procedure p, int phase, int order) {
        if (p == null) return;
        if (phase < 0 || order < 0) return;

        if (p.getLastPhaseIndex() < 0) {
            p.setLastPhaseIndex(phase);
            p.setLastOrderIndex(order);
            return;
        }

        if (phase > p.getLastPhaseIndex()) {
            p.setLastPhaseIndex(phase);
            p.setLastOrderIndex(order);
        } else if (phase == p.getLastPhaseIndex() && order > p.getLastOrderIndex()) {
            p.setLastOrderIndex(order);
        }
    }
}

