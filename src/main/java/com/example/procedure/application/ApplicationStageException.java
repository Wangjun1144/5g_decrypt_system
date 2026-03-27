package com.example.procedure.application;

/**
 * Runtime exception enriched with application-stage context.
 *
 * It gives top-level entry points a stable way to surface which stage failed
 * and which business reference was involved.
 */
public class ApplicationStageException extends RuntimeException {

    /**
     * Name of the failed stage.
     */
    private final String stage;

    /**
     * Business reference associated with the failed stage, for example a pcap
     * path or message identifier.
     */
    private final String reference;

    public ApplicationStageException(String stage, String reference, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.reference = reference;
    }

    public String getStage() {
        return stage;
    }

    public String getReference() {
        return reference;
    }
}
