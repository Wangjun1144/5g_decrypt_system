package com.example.procedure.processing.procedure.stage;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.processing.dispatch.ProcedureDispatchOutcome;
import com.example.procedure.processing.procedure.recognize.ProcedureRecognitionOutcome;

/**
 * Typed outcome for the procedure stage so the main coordinator can observe
 * what this stage actually produced.
 */
public class ProcedureStageOutcome {

    private final boolean procedureMessage;
    private final ProcedureRecognitionOutcome recognitionOutcome;
    private final ProcedureDispatchOutcome dispatchOutcome;

    private ProcedureStageOutcome(
            boolean procedureMessage,
            ProcedureRecognitionOutcome recognitionOutcome,
            ProcedureDispatchOutcome dispatchOutcome
    ) {
        this.procedureMessage = procedureMessage;
        this.recognitionOutcome = recognitionOutcome;
        this.dispatchOutcome = dispatchOutcome;
    }

    /**
     * Creates one immutable procedure-stage outcome.
     */
    public static ProcedureStageOutcome of(
            boolean procedureMessage,
            ProcedureRecognitionOutcome recognitionOutcome,
            ProcedureDispatchOutcome dispatchOutcome
    ) {
        return new ProcedureStageOutcome(procedureMessage, recognitionOutcome, dispatchOutcome);
    }

    public boolean isProcedureMessage() {
        return procedureMessage;
    }

    public boolean isDispatched() {
        return dispatchOutcome != null && dispatchOutcome.isDispatched();
    }

    public boolean hasMatchResult() {
        return recognitionOutcome != null && recognitionOutcome.getMatchResult() != null;
    }

    public ProcedureMatchResult getMatchResult() {
        return recognitionOutcome == null ? null : recognitionOutcome.getMatchResult();
    }

    public ProcedureRecognitionOutcome getRecognitionOutcome() {
        return recognitionOutcome;
    }

    public ProcedureDispatchOutcome getDispatchOutcome() {
        return dispatchOutcome;
    }
}
