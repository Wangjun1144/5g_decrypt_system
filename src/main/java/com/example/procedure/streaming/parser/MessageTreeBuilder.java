package com.example.procedure.streaming.parser;


import com.example.procedure.model.tree.MessageNode;
import com.example.procedure.model.tree.MessageNodeType;
import com.example.procedure.model.tree.MessageTree;
import com.example.procedure.streaming.index.ChainIndex;
import com.example.procedure.model.tree.PayloadRef;
import com.example.procedure.model.tree.PayloadType;

import com.example.procedure.model.tree.*;
import com.example.procedure.streaming.index.ChainIndex;
import com.example.procedure.streaming.index.MsgNode;
import com.example.procedure.streaming.index.MsgType;

import java.util.ArrayList;
import java.util.List;

public class MessageTreeBuilder {

    public static MessageTree fromChainIndex(String msgId, ChainIndex index) {
        MessageTree tree = new MessageTree();
        tree.setRootNodeId(msgId + ":ROOT");

        for (MsgNode raw : index.nodes()) {
            String nodeId = toNodeId(msgId, raw);
            if (nodeId == null) continue;

            MessageNode node = new MessageNode();
            node.setNodeId(nodeId);
            node.setPath(index.pathOf(raw.pathId));
            node.setNodeType(mapType(raw.type));
            node.setPayloadIndex(raw.payloadIndex);
            node.setPayloadSequence(raw.payloadSequence);

            String parentNodeId = toParentNodeId(msgId, index, raw);
            node.setParentNodeId(parentNodeId);

            tree.getNodesById().put(nodeId, node);
        }

        for (MsgNode raw : index.nodes()) {
            String nodeId = toNodeId(msgId, raw);
            if (nodeId == null) continue;

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

    private static String toNodeId(String msgId, MsgNode raw) {
        if (raw == null) return null;
        if (raw.type == MsgType.PACKET) return msgId + ":ROOT";
        if (raw.payloadSequence < 0) return null;
        return msgId + ":N" + raw.payloadSequence;
    }

    private static String toParentNodeId(String msgId, ChainIndex index, MsgNode raw) {
        if (raw == null || raw.parentId < 0) return null;

        MsgNode parent = index.node(raw.parentId);
        if (parent == null) return null;

        if (parent.type == MsgType.PACKET) {
            return msgId + ":ROOT";
        }
        if (parent.payloadSequence < 0) {
            return null;
        }
        return msgId + ":N" + parent.payloadSequence;
    }

    private static String buildNodeId(String messageId, int rawNodeId) {
        return messageId + ":N" + rawNodeId;
    }

    private static MessageNodeType mapType(MsgType type) {
        if (type == null) return MessageNodeType.UNKNOWN;
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

    private static PayloadRef buildPayloadRef(MsgNode raw) {
        if (raw.payloadIndex < 0 || raw.type == null) {
            return null;
        }

        PayloadType payloadType = switch (raw.type) {
            case MAC -> PayloadType.MAC;
            case PDCP -> PayloadType.PDCP;
            case RRC -> PayloadType.RRC;
            case NAS -> PayloadType.NAS;
            case NGAP -> PayloadType.NGAP;
            case NUAR -> PayloadType.NUAR;
            default -> null;
        };

        if (payloadType == null) {
            return null;
        }

        return new PayloadRef(payloadType, raw.payloadIndex);
    }
}