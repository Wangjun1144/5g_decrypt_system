package com.example.procedure.infrastructure.decode.nativews;

import com.example.procedure.infrastructure.dissection.field.DecodedFieldNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps the native JSON envelope into project-owned result types.
 */
@Component
public class NativeWiresharkResultMapper {

    private final ObjectMapper objectMapper;

    public NativeWiresharkResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public NativeWiresharkNasResult mapNas5gs(String rawJson) {
        Objects.requireNonNull(rawJson, "rawJson must not be null");
        try {
            NativeNasJsonEnvelope envelope = objectMapper.readValue(rawJson, NativeNasJsonEnvelope.class);
            Map<String, String> flatFields = envelope.flatFields == null
                    ? Map.of()
                    : new LinkedHashMap<>(envelope.flatFields);
            List<DecodedFieldNode> fieldTree = envelope.fieldTree == null
                    ? List.of()
                    : envelope.fieldTree.stream().map(this::toFieldNode).toList();
            List<String> diagnostics = envelope.diagnostics == null
                    ? List.of()
                    : List.copyOf(envelope.diagnostics);
            return new NativeWiresharkNasResult(
                    envelope.bridgeVersion,
                    envelope.protocolName,
                    envelope.messageType,
                    envelope.messageTypeName,
                    flatFields,
                    fieldTree,
                    diagnostics
            );
        } catch (JsonProcessingException e) {
            throw new NativeWiresharkBridgeException("Failed to map native Wireshark result JSON", e);
        }
    }

    private DecodedFieldNode toFieldNode(NativeFieldNodeDto dto) {
        DecodedFieldNode node = new DecodedFieldNode(
                dto.name == null ? "" : dto.name,
                dto.value == null ? "" : dto.value,
                dto.offset,
                dto.length
        );
        if (dto.children != null) {
            for (NativeFieldNodeDto child : dto.children) {
                node.addChild(toFieldNode(child));
            }
        }
        return node;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class NativeNasJsonEnvelope {
        public String bridgeVersion;
        public String protocolName;
        public int messageType = -1;
        public String messageTypeName;
        public Map<String, String> flatFields = Map.of();
        public List<NativeFieldNodeDto> fieldTree = List.of();
        public List<String> diagnostics = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class NativeFieldNodeDto {
        public String name;
        public String value;
        public int offset;
        public int length;
        public List<NativeFieldNodeDto> children = new ArrayList<>();
    }
}
