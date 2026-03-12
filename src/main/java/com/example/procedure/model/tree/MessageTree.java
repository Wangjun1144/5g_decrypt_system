package com.example.procedure.model.tree;

import lombok.Data;
import lombok.Setter;

import java.util.*;

import java.util.*;
@Data
public class MessageTree {
    private String rootNodeId; // 例如 MSG-10:ROOT
    private Map<String, MessageNode> nodesById = new LinkedHashMap<>();

    public MessageNode getNode(String nodeId) {
        return nodesById.get(nodeId);
    }

    public MessageNode getParent(String nodeId) {
        MessageNode n = getNode(nodeId);
        return (n == null || n.getParentNodeId() == null) ? null : getNode(n.getParentNodeId());
    }

    public List<MessageNode> getChildren(String nodeId) {
        MessageNode n = getNode(nodeId);
        if (n == null) return List.of();
        List<MessageNode> out = new ArrayList<>();
        for (String childId : n.getChildNodeIds()) {
            MessageNode child = getNode(childId);
            if (child != null) out.add(child);
        }
        return out;
    }
}