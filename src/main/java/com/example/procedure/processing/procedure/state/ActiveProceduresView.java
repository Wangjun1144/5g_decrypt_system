package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;

import java.util.List;

/**
 * Typed query view for active procedures of one UE.
 */
public class ActiveProceduresView {

    private final int count;
    private final List<Procedure> procedures;

    private ActiveProceduresView(int count, List<Procedure> procedures) {
        this.count = count;
        this.procedures = procedures == null ? List.of() : List.copyOf(procedures);
    }

    /**
     * Creates a typed active-procedure view from the current list snapshot.
     */
    public static ActiveProceduresView of(List<Procedure> procedures) {
        List<Procedure> safeProcedures = procedures == null ? List.of() : procedures;
        return new ActiveProceduresView(safeProcedures.size(), safeProcedures);
    }

    /**
     * Returns how many active procedures currently exist for the UE.
     */
    public int getCount() {
        return count;
    }

    /**
     * Returns the immutable active procedure list snapshot.
     */
    public List<Procedure> getProcedures() {
        return procedures;
    }
}
