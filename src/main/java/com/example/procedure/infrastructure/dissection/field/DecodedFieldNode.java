package com.example.procedure.infrastructure.dissection.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal protocol-field tree node inspired by Wireshark's proto_tree usage.
 */
public class DecodedFieldNode {

    private final String name;
    private final String value;
    private final int offset;
    private final int length;
    private final List<DecodedFieldNode> children = new ArrayList<>();

    public DecodedFieldNode(String name, String value, int offset, int length) {
        this.name = name;
        this.value = value;
        this.offset = offset;
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public void addChild(DecodedFieldNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public List<DecodedFieldNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
