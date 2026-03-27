package com.example.procedure.processing.binding.stage;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.MessageSourceType;

/**
 * Internal binding-stage command used by processing code.
 *
 * Application-layer request objects are mapped into this command at the
 * application boundary so processing code does not depend on application
 * packages anymore.
 */
public class BindingStageCommand {

    private final SignalingMessage message;
    private final MessageSourceType sourceType;
    private final String sourceName;
    private final String correlationId;
    private final boolean reentry;

    public BindingStageCommand(
            SignalingMessage message,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.correlationId = correlationId;
        this.reentry = reentry;
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
}
