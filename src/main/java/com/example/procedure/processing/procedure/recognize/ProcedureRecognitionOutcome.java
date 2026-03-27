package com.example.procedure.processing.procedure.recognize;

import com.example.procedure.model.ProcedureMatchResult;

/**
 * Typed outcome for procedure recognition.
 */
public class ProcedureRecognitionOutcome {

    private final ProcedureMatchResult matchResult;
    private final boolean recognizedFromTrigger;
    private final boolean recognizedFromBestMatch;
    private final boolean fellBackToUnknown;

    private ProcedureRecognitionOutcome(
            ProcedureMatchResult matchResult,
            boolean recognizedFromTrigger,
            boolean recognizedFromBestMatch,
            boolean fellBackToUnknown
    ) {
        this.matchResult = matchResult;
        this.recognizedFromTrigger = recognizedFromTrigger;
        this.recognizedFromBestMatch = recognizedFromBestMatch;
        this.fellBackToUnknown = fellBackToUnknown;
    }

    /**
     * Creates one immutable recognition outcome.
     */
    public static ProcedureRecognitionOutcome of(
            ProcedureMatchResult matchResult,
            boolean recognizedFromTrigger,
            boolean recognizedFromBestMatch,
            boolean fellBackToUnknown
    ) {
        return new ProcedureRecognitionOutcome(
                matchResult,
                recognizedFromTrigger,
                recognizedFromBestMatch,
                fellBackToUnknown
        );
    }

    public ProcedureMatchResult getMatchResult() {
        return matchResult;
    }

    public boolean isRecognizedFromTrigger() {
        return recognizedFromTrigger;
    }

    public boolean isRecognizedFromBestMatch() {
        return recognizedFromBestMatch;
    }

    public boolean isFellBackToUnknown() {
        return fellBackToUnknown;
    }
}
