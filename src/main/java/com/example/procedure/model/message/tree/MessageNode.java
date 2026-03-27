package com.example.procedure.model.message.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息树节点。
 *
 * 当前阶段显式补齐关键 getter/setter，
 * 避免主干编译继续受 Lombok 生成差异影响。
 */
public class MessageNode {

    private String nodeId;
    private String parentNodeId;
    private List<String> childNodeIds = new ArrayList<>();
    private String path;
    private MessageNodeType nodeType;
    private int payloadIndex;
    private int payloadSequence;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getParentNodeId() {
        return parentNodeId;
    }

    public void setParentNodeId(String parentNodeId) {
        this.parentNodeId = parentNodeId;
    }

    public List<String> getChildNodeIds() {
        return childNodeIds;
    }

    public void setChildNodeIds(List<String> childNodeIds) {
        this.childNodeIds = childNodeIds == null ? new ArrayList<>() : childNodeIds;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public MessageNodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(MessageNodeType nodeType) {
        this.nodeType = nodeType;
    }

    public int getPayloadIndex() {
        return payloadIndex;
    }

    public void setPayloadIndex(int payloadIndex) {
        this.payloadIndex = payloadIndex;
    }

    public int getPayloadSequence() {
        return payloadSequence;
    }

    public void setPayloadSequence(int payloadSequence) {
        this.payloadSequence = payloadSequence;
    }
}
