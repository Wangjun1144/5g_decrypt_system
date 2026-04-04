package com.example.procedure.infrastructure.dissection.nas;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of the isolated structured NAS dissection path.
 */
public class Nas5gsStructuredDecodeResult {

    private final int messageType;
    private final String messageTypeName;
    private final Map<String, String> decodedFields;
    private final List<DecodedFieldNode> fieldTree;

    public Nas5gsStructuredDecodeResult(
            int messageType,
            String messageTypeName,
            Map<String, String> decodedFields,
            List<DecodedFieldNode> fieldTree
    ) {
        this.messageType = messageType;
        this.messageTypeName = messageTypeName;
        this.decodedFields = Collections.unmodifiableMap(new LinkedHashMap<>(decodedFields));
        this.fieldTree = Collections.unmodifiableList(fieldTree);
    }

    public int getMessageType() {
        return messageType;
    }

    public String getMessageTypeName() {
        return messageTypeName;
    }

    public Map<String, String> getDecodedFields() {
        return decodedFields;
    }

    public List<DecodedFieldNode> getFieldTree() {
        return fieldTree;
    }
}
