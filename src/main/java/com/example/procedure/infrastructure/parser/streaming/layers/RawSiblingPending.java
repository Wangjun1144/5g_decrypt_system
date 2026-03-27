package com.example.procedure.infrastructure.parser.streaming.layers;

/**
 * Tracks one pending raw sibling awaiting its matching logical field.
 *
 * The parser uses strict sibling matching semantics: a raw field only applies to
 * the immediately following logical sibling with the same base name, otherwise it
 * is discarded when the next sibling boundary is crossed.
 */
public class RawSiblingPending {

    private String logicField;
    private String rawHex;

    /**
     * Arms one pending raw match candidate.
     */
    public void arm(String logicField, String rawHex) {
        clear();
        if (rawHex != null) {
            this.logicField = logicField;
            this.rawHex = rawHex;
        }
    }

    /**
     * Returns the pending raw hex when the next sibling matches the expected logical field.
     */
    public String consumeIfMatches(String actualField) {
        String matched = null;
        if (logicField != null && logicField.equals(actualField)) {
            matched = rawHex;
        }
        clear();
        return matched;
    }

    /**
     * Returns whether a pending raw sibling is currently armed.
     */
    public boolean isArmed() {
        return logicField != null;
    }

    public String getLogicField() {
        return logicField;
    }

    public String getRawHex() {
        return rawHex;
    }

    /**
     * Clears the current pending raw sibling regardless of match outcome.
     */
    public void clear() {
        this.logicField = null;
        this.rawHex = null;
    }
}
