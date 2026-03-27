package com.example.procedure.infrastructure.parser;

import com.example.procedure.support.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

/**
 * Detects protocol message type names from packet subtrees.
 */
public class PacketProtocolTypeDetector {

    /**
     * Detects one NGAP message type from {@code ngap.value_element}.
     */
    public String detectNgapMessageType(JsonNode msgElemNode) {
        JsonNode valueElem = JsonUtils.path(msgElemNode, "ngap.value_element");
        if (valueElem == null || valueElem.isMissingNode() || !valueElem.isObject()) {
            return null;
        }

        Iterator<String> fieldNames = valueElem.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (field.startsWith("ngap.") && field.endsWith("_element")) {
                return field.substring("ngap.".length(), field.length() - "_element".length());
            }
        }
        return null;
    }

    /**
     * Detects one RRC message type from {@code nr-rrc.c1_tree}.
     */
    public String detectRrcMessageType(JsonNode msgElemNode) {
        JsonNode c1Tree = JsonUtils.path(
                msgElemNode,
                "nr-rrc.message_tree",
                "nr-rrc.c1_tree"
        );
        if (c1Tree == null || c1Tree.isMissingNode()) {
            return null;
        }

        Iterator<String> fieldNames = c1Tree.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (field.startsWith("nr-rrc.") && field.endsWith("_element")) {
                return field.substring("nr-rrc.".length(), field.length() - "_element".length());
            }
        }
        return null;
    }
}
