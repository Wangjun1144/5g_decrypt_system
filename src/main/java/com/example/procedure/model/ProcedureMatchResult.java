package com.example.procedure.model;

/**
 * Result returned by the procedure recognition stage.
 */
public class ProcedureMatchResult {

    private int status;
    private String message;
    private String procedureId;
    private ProcedureTypeEnum procedureType;
    private boolean newProcedure;

    public ProcedureMatchResult() {
    }

    public ProcedureMatchResult(
            int status,
            String message,
            String procedureId,
            ProcedureTypeEnum procedureType,
            boolean newProcedure
    ) {
        this.status = status;
        this.message = message;
        this.procedureId = procedureId;
        this.procedureType = procedureType;
        this.newProcedure = newProcedure;
    }

    public static ProcedureMatchResult successNew(String procedureId, ProcedureTypeEnum type) {
        return new ProcedureMatchResult(0, null, procedureId, type, true);
    }

    public static ProcedureMatchResult successExisting(String procedureId, ProcedureTypeEnum type) {
        return new ProcedureMatchResult(0, null, procedureId, type, false);
    }

    public static ProcedureMatchResult notMatched(String message) {
        return new ProcedureMatchResult(1, message, null, null, false);
    }

    public static ProcedureMatchResult error(String message) {
        return new ProcedureMatchResult(2, message, null, null, false);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProcedureId() {
        return procedureId;
    }

    public void setProcedureId(String procedureId) {
        this.procedureId = procedureId;
    }

    public ProcedureTypeEnum getProcedureType() {
        return procedureType;
    }

    public void setProcedureType(ProcedureTypeEnum procedureType) {
        this.procedureType = procedureType;
    }

    public boolean isNewProcedure() {
        return newProcedure;
    }

    public void setNewProcedure(boolean newProcedure) {
        this.newProcedure = newProcedure;
    }
}
