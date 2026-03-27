package com.example.procedure.application.message;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.MessageSourceType;

import java.util.UUID;

/**
 * Application-layer ingress request for one signaling message.
 *
 * The request wraps the message together with source metadata so the
 * application edge can keep track of where the message came from and whether
 * it re-entered from a downstream stage.
 */
public class SignalingMessageIngressRequest {

    private final SignalingMessage message;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final String correlationId;
    private final boolean reentry;

    public SignalingMessageIngressRequest(
            SignalingMessage message,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.sourceType = sourceType == null ? MessageSourceType.DIRECT : sourceType;
        this.sourceName = sourceName;
        this.correlationId = normalizeCorrelationId(correlationId);
        this.reentry = reentry;
    }

    /**
     * Build a direct-ingress request from an in-memory signaling message.
     */
    public static SignalingMessageIngressRequest of(SignalingMessage message) {
        return new SignalingMessageIngressRequest(
                message,
                MessageSourceType.DIRECT,
                null,
                null,
                false
        );
    }

    /**
     * Build an ingress request whose source is pcap ingestion.
     */
    public static SignalingMessageIngressRequest fromPcap(
            SignalingMessage message,
            String sourceName,
            String correlationId
    ) {
        return new SignalingMessageIngressRequest(
                message,
                MessageSourceType.PCAP,
                sourceName,
                correlationId,
                false
        );
    }

    /**
     * Build an ingress request for a message re-entering from downstream logic.
     */
    public static SignalingMessageIngressRequest reentry(
            SignalingMessage message,
            String sourceName,
            String correlationId
    ) {
        return new SignalingMessageIngressRequest(
                message,
                MessageSourceType.REENTRY,
                sourceName,
                correlationId,
                true
        );
    }

    public SignalingMessage getMessage() {
        return message;
    }

    public MessageSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isReentry() {
        return reentry;
    }

    private static String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }
}
