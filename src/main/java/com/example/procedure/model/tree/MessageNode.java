package com.example.procedure.model.tree;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class MessageNode {
    private String nodeId;
    private String parentNodeId;
    private List<String> childNodeIds = new ArrayList<>();

    private String path;
    private MessageNodeType nodeType;
    private int payloadIndex;
    private int payloadSequence;
}