package com.example.procedure.infrastructure.decode.nativews;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured phase-1 native decode output aligned with the project's internal
 * field-tree model.
 */
public final class NativeWiresharkNasResult {

    private final String bridgeVersion;
    private final String protocolName;
    private final int messageType;
    private final String messageTypeName;
    private final Map<String, String> flatFields;
    private final List<DecodedFieldNode> fieldTree;
    private final List<String> diagnostics;

    public NativeWiresharkNasResult(
            String bridgeVersion,
            String protocolName,
            int messageType,
            String messageTypeName,
            Map<String, String> flatFields,
            List<DecodedFieldNode> fieldTree,
            List<String> diagnostics
    ) {
        this.bridgeVersion = Objects.requireNonNullElse(bridgeVersion, "unknown");
        this.protocolName = Objects.requireNonNullElse(protocolName, "");
        this.messageType = messageType;
        this.messageTypeName = Objects.requireNonNullElse(messageTypeName, "");
        this.flatFields = Map.copyOf(Objects.requireNonNull(flatFields, "flatFields must not be null"));
        this.fieldTree = List.copyOf(Objects.requireNonNull(fieldTree, "fieldTree must not be null"));
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }

    public String getBridgeVersion() {
        return bridgeVersion;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public int getMessageType() {
        return messageType;
    }

    public String getMessageTypeName() {
        return messageTypeName;
    }

    public Map<String, String> getFlatFields() {
        return flatFields;
    }

    public List<DecodedFieldNode> getFieldTree() {
        return fieldTree;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }
}
