package com.example.procedure.model.message.tree;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree-form representation of one signaling message.
 *
 * The tree links protocol-layer nodes by id so later stages can navigate
 * parent/child relationships and attach decrypt reentry results precisely.
 */
@Data
public class MessageTree {

    private String rootNodeId;
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
        if (n == null) {
            return List.of();
        }
        List<MessageNode> out = new ArrayList<>();
        for (String childId : n.getChildNodeIds()) {
            MessageNode child = getNode(childId);
            if (child != null) {
                out.add(child);
            }
        }
        return out;
    }
}
