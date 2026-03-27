package com.example.procedure.processing.message.classify;

import com.example.procedure.model.MessageCategory;

/**
 * Typed outcome for the classification stage.
 */
public class MessageClassificationOutcome {

    private final MessageCategory category;

    private MessageClassificationOutcome(MessageCategory category) {
        this.category = category;
    }

    /**
     * Creates one immutable classification outcome.
     */
    public static MessageClassificationOutcome of(MessageCategory category) {
        return new MessageClassificationOutcome(category);
    }

    public MessageCategory getCategory() {
        return category;
    }
}
