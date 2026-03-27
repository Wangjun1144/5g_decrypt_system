package com.example.procedure.infrastructure.parser.streaming.index;

import java.util.ArrayList;
import java.util.List;

/**
 * One node inside a streaming chain index.
 *
 * Nodes record hierarchy, traversal intervals, and payload lookup metadata so
 * higher-level components can reconstruct normalized message trees.
 */
public final class MsgNode {

    /**
     * Stable node identifier within the chain index.
     */
    public int id;

    /**
     * Node type.
     */
    public MsgType type;

    /**
     * Enter timestamp in the traversal sequence.
     */
    public int enter;

    /**
     * Exit timestamp in the traversal sequence.
     */
    public int exit;

    /**
     * Traversal depth when the node was entered.
     */
    public int depth;

    /**
     * Parent node id, or {@code -1} when this node is the root.
     */
    public int parentId;

    /**
     * Child node ids in encounter order.
     */
    public List<Integer> children = new ArrayList<>(2);

    /**
     * Payload index inside the parsed-result list, or {@code -1} for the
     * synthetic packet-root node.
     */
    public int payloadIndex;

    /**
     * Interned path-table id for the node path.
     */
    public int pathId;

    /**
     * Payload creation sequence used to derive stable downstream node ids, or
     * {@code -1} for the synthetic packet-root node.
     */
    public int payloadSequence;
}
