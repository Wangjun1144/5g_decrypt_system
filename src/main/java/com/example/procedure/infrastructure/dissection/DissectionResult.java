package com.example.procedure.infrastructure.dissection;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dissection result for the current migration stage.
 *
 * <p>The initial goal is to prove stable dispatch and protocol-entry tracing
 * before implementing concrete field extraction.</p>
 */
public class DissectionResult {

    private final String entryProtocol;
    private final String entryShortName;
    private final String entryDisplayName;
    private final List<String> protocolTrace;
    private final Map<String, String> decodedFields;
    private final List<DecodedFieldNode> fieldTree;

    private DissectionResult(
            String entryProtocol,
            String entryShortName,
            String entryDisplayName,
            List<String> protocolTrace,
            Map<String, String> decodedFields,
            List<DecodedFieldNode> fieldTree
    ) {
        this.entryProtocol = entryProtocol;
        this.entryShortName = entryShortName;
        this.entryDisplayName = entryDisplayName;
        this.protocolTrace = Collections.unmodifiableList(new ArrayList<>(protocolTrace));
        this.decodedFields = Collections.unmodifiableMap(new LinkedHashMap<>(decodedFields));
        this.fieldTree = Collections.unmodifiableList(new ArrayList<>(fieldTree));
    }

    public static DissectionResult of(
            String entryProtocol,
            String entryShortName,
            String entryDisplayName,
            List<String> protocolTrace
    ) {
        return new DissectionResult(
                entryProtocol,
                entryShortName,
                entryDisplayName,
                protocolTrace == null ? List.of() : protocolTrace,
                Map.of(),
                List.of()
        );
    }

    public static DissectionResult of(
            String entryProtocol,
            String entryShortName,
            String entryDisplayName,
            List<String> protocolTrace,
            Map<String, String> decodedFields
    ) {
        return new DissectionResult(
                entryProtocol,
                entryShortName,
                entryDisplayName,
                protocolTrace == null ? List.of() : protocolTrace,
                decodedFields == null ? Map.of() : decodedFields,
                List.of()
        );
    }

    public static DissectionResult of(
            String entryProtocol,
            String entryShortName,
            String entryDisplayName,
            List<String> protocolTrace,
            Map<String, String> decodedFields,
            List<DecodedFieldNode> fieldTree
    ) {
        return new DissectionResult(
                entryProtocol,
                entryShortName,
                entryDisplayName,
                protocolTrace == null ? List.of() : protocolTrace,
                decodedFields == null ? Map.of() : decodedFields,
                fieldTree == null ? List.of() : fieldTree
        );
    }

    public String getEntryProtocol() {
        return entryProtocol;
    }

    public String getEntryShortName() {
        return entryShortName;
    }

    public String getEntryDisplayName() {
        return entryDisplayName;
    }

    public List<String> getProtocolTrace() {
        return protocolTrace;
    }

    public Map<String, String> getDecodedFields() {
        return decodedFields;
    }

    public List<DecodedFieldNode> getFieldTree() {
        return fieldTree;
    }
}
