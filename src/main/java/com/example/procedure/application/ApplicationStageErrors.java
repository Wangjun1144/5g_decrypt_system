package com.example.procedure.application;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.StageLogRefs;

import java.nio.file.Path;

/**
 * Factory methods for consistent application-stage exceptions.
 *
 * This keeps top-level application entry points aligned on the same exception
 * shape without duplicating reference-building logic.
 */
public final class ApplicationStageErrors {

    private ApplicationStageErrors() {
    }

    /**
     * Build one stage exception associated with a pcap file.
     */
    public static ApplicationStageException forPcap(
            String stage,
            Path pcap,
            String message,
            Throwable cause
    ) {
        String reference = pcap == null ? null : pcap.toString();
        return new ApplicationStageException(
                stage,
                reference,
                withReference(message, reference),
                cause
        );
    }

    /**
     * Build one stage exception associated with one signaling message.
     */
    public static ApplicationStageException forMessage(
            String stage,
            SignalingMessage msg,
            String message,
            Throwable cause
    ) {
        String reference = StageLogRefs.message(msg);
        return new ApplicationStageException(
                stage,
                reference,
                withReference(message, reference),
                cause
        );
    }

    /**
     * Append a normalized reference suffix to a stage message.
     */
    public static String withReference(String message, String reference) {
        if (reference == null || reference.isBlank()) {
            return message;
        }
        return message + " [ref=" + reference + "]";
    }
}
