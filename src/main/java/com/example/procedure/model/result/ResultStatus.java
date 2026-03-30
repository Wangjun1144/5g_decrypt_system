package com.example.procedure.model.result;

/**
 * Normalized status values for high-level result summaries.
 */
public enum ResultStatus {

    /**
     * The operation completed successfully.
     */
    SUCCESS,

    /**
     * The operation was intentionally skipped.
     */
    SKIPPED,

    /**
     * The operation entered a buffered or waiting state instead of failing.
     */
    BUFFERED,

    /**
     * The operation failed.
     */
    FAILED
}
