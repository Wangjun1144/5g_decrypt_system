package com.example.procedure.infrastructure.parser.streaming.parser;

import com.example.procedure.model.message.tree.MessageNode;
import com.example.procedure.model.message.tree.MessageNodeType;
import com.example.procedure.model.message.tree.MessageTree;
import com.example.procedure.infrastructure.parser.streaming.index.ChainIndex;
import com.example.procedure.infrastructure.parser.streaming.index.MsgNode;
import com.example.procedure.infrastructure.parser.streaming.index.MsgType;

/**
 * Builds a normalized {@link MessageTree} from a streaming chain index.
 */
public final class MessageTreeBuilder {

    private MessageTreeBuilder() {
    }

    /**
     * Convert one chain index into a message tree rooted at {@code msgId:ROOT}.
     *
     * @param msgId message identifier
     * @param index parsed chain index
     * @return normalized message tree
     */
    public static MessageTree fromChainIndex(String msgId, ChainIndex index) {
        MessageTree tree = new MessageTree();
        tree.setRootNodeId(msgId + ":ROOT");

        for (MsgNode raw : index.nodes()) {
            String nodeId = toNodeId(msgId, raw);
            if (nodeId == null) {
                continue;
            }

            MessageNode node = new MessageNode();
            node.setNodeId(nodeId);
            node.setPath(index.pathOf(raw.pathId));
            node.setNodeType(mapType(raw.type));
            node.setPayloadIndex(raw.payloadIndex);
            node.setPayloadSequence(raw.payloadSequence);
            node.setParentNodeId(toParentNodeId(msgId, index, raw));

            tree.getNodesById().put(nodeId, node);
        }

        for (MsgNode raw : index.nodes()) {
            String nodeId = toNodeId(msgId, raw);
            if (nodeId == null) {
                continue;
            }

            MessageNode node = tree.getNode(nodeId);
            for (Integer childRawId : raw.children) {
                MsgNode childRaw = index.node(childRawId);
                String childNodeId = toNodeId(msgId, childRaw);
                if (childNodeId != null) {
                    node.getChildNodeIds().add(childNodeId);
                }
            }
        }

        return tree;
    }

    /**
     * Convert one indexed node into the stable message-tree node id.
     */
    private static String toNodeId(String msgId, MsgNode raw) {
        if (raw == null) {
            return null;
        }
        if (raw.type == MsgType.PACKET) {
            return msgId + ":ROOT";
        }
        if (raw.payloadSequence < 0) {
            return null;
        }
        return msgId + ":N" + raw.payloadSequence;
    }

    /**
     * Resolve the stable parent node id for one indexed node.
     */
    private static String toParentNodeId(String msgId, ChainIndex index, MsgNode raw) {
        if (raw == null || raw.parentId < 0) {
            return null;
        }

        MsgNode parent = index.node(raw.parentId);
        if (parent == null) {
            return null;
        }

        if (parent.type == MsgType.PACKET) {
            return msgId + ":ROOT";
        }
        if (parent.payloadSequence < 0) {
            return null;
        }
        return msgId + ":N" + parent.payloadSequence;
    }

    /**
     * Map streaming index node types to normalized message-tree node types.
     */
    private static MessageNodeType mapType(MsgType type) {
        if (type == null) {
            return MessageNodeType.UNKNOWN;
        }
        return switch (type) {
            case PACKET -> MessageNodeType.PACKET;
            case MAC -> MessageNodeType.MAC;
            case PDCP -> MessageNodeType.PDCP;
            case RRC -> MessageNodeType.RRC;
            case NAS -> MessageNodeType.NAS;
            case NGAP -> MessageNodeType.NGAP;
            case NUAR -> MessageNodeType.NUAR;
        };
    }
}
