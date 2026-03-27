package com.example.procedure.processing.dispatch;

/**
 * Typed outcome for procedure dispatch.
 */
public class ProcedureDispatchOutcome {

    private final boolean dispatched;
    private final boolean ueContextUpdated;
    private final String procedureId;
    private final String procedureTypeCode;

    private ProcedureDispatchOutcome(
            boolean dispatched,
            boolean ueContextUpdated,
            String procedureId,
            String procedureTypeCode
    ) {
        this.dispatched = dispatched;
        this.ueContextUpdated = ueContextUpdated;
        this.procedureId = procedureId;
        this.procedureTypeCode = procedureTypeCode;
    }

    /**
     * Creates one immutable dispatch outcome.
     */
    public static ProcedureDispatchOutcome of(
            boolean dispatched,
            boolean ueContextUpdated,
            String procedureId,
            String procedureTypeCode
    ) {
        return new ProcedureDispatchOutcome(dispatched, ueContextUpdated, procedureId, procedureTypeCode);
    }

    public boolean isDispatched() {
        return dispatched;
    }

    public boolean isUeContextUpdated() {
        return ueContextUpdated;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public String getProcedureTypeCode() {
        return procedureTypeCode;
    }
}
