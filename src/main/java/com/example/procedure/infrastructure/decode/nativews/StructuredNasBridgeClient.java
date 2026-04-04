package com.example.procedure.infrastructure.decode.nativews;

import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDecodeResult;
import com.example.procedure.infrastructure.dissection.nas.Nas5gsStructuredDissector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase-1 fallback bridge that keeps the native-facing JSON contract stable
 * while delegating decode work to the project-owned structured NAS dissector.
 */
public class StructuredNasBridgeClient implements NativeWiresharkBridgeClient {

    private final Nas5gsStructuredDissector structuredDissector;
    private final ObjectMapper objectMapper;

    public StructuredNasBridgeClient(
            Nas5gsStructuredDissector structuredDissector,
            ObjectMapper objectMapper
    ) {
        this.structuredDissector = Objects.requireNonNull(structuredDissector, "structuredDissector must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public String decodeNas5gs(NativeWiresharkNasRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getPayload().length == 0) {
            throw new NativeWiresharkBridgeException("NAS payload must not be empty");
        }

        Nas5gsStructuredDecodeResult result = structuredDissector.dissect(PacketBuffer.wrap(request.getPayload()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("bridgeVersion", "phase1-java-structured");
        envelope.put("protocolName", "nas-5gs");
        envelope.put("messageType", result.getMessageType());
        envelope.put("messageTypeName", result.getMessageTypeName());
        envelope.put("flatFields", result.getDecodedFields());
        envelope.put("fieldTree", request.isIncludeFieldTree() ? mapTree(result.getFieldTree(), request.isIncludeOffsets()) : List.of());
        envelope.put("diagnostics", List.of("decoded by project-owned structured NAS dissector"));

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new NativeWiresharkBridgeException("Failed to serialize structured NAS decode result", e);
        }
    }

    private List<Map<String, Object>> mapTree(List<DecodedFieldNode> nodes, boolean includeOffsets) {
        return nodes.stream().map(node -> mapNode(node, includeOffsets)).toList();
    }

    private Map<String, Object> mapNode(DecodedFieldNode node, boolean includeOffsets) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("name", node.getName());
        mapped.put("value", node.getValue());
        mapped.put("offset", includeOffsets ? node.getOffset() : -1);
        mapped.put("length", includeOffsets ? node.getLength() : -1);
        mapped.put("children", mapTree(node.getChildren(), includeOffsets));
        return mapped;
    }
}
