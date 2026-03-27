package com.example.procedure.infrastructure.parser.streaming.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

/**
 * Hierarchical index for one parsed streaming chain.
 *
 * The index records node enter/exit intervals, parent-child relationships,
 * type-based lookup tables, and interned paths so later stages can reconstruct
 * stable message trees without retaining the original JSON tree.
 */
public final class ChainIndex {

    private int time = 0;
    private long typeMask = 0L;
    private final ArrayList<MsgNode> nodes = new ArrayList<>(16);
    private final ArrayDeque<Integer> stack = new ArrayDeque<>(16);

    private final EnumMap<MsgType, ArrayList<Integer>> byType = new EnumMap<>(MsgType.class);

    private final ArrayList<String> pathTable = new ArrayList<>(32);
    private final HashMap<String, Integer> pathToId = new HashMap<>(64);
    private final HashMap<Integer, MsgNode> nodeById = new HashMap<>(16);

    /**
     * Start a synthetic packet-root node for the current chain.
     *
     * @param rootPath packet-root path label
     * @param depth traversal depth
     */
    public void startPacketRoot(String rootPath, int depth) {
        onEnter(MsgType.PACKET, depth, rootPath, -1, -1);
    }

    /**
     * Close the synthetic packet-root node.
     */
    public void endPacketRoot() {
        onExit();
    }

    /**
     * Enter one indexed node.
     *
     * @param type node type
     * @param depth traversal depth
     * @param path logical path
     * @param payloadIndex payload index in the parsed-result list
     * @param payloadSequence payload creation sequence
     * @return node id
     */
    public int onEnter(MsgType type, int depth, String path, int payloadIndex, int payloadSequence) {
        typeMask |= (1L << type.ordinal());
        MsgNode node = new MsgNode();
        node.id = payloadSequence >= 0 ? payloadSequence : nodes.size();
        node.payloadIndex = payloadIndex;
        node.payloadSequence = payloadSequence;
        node.type = type;
        node.depth = depth;
        node.enter = ++time;
        node.exit = -1;
        node.parentId = stack.isEmpty() ? -1 : stack.peekLast();
        node.pathId = internPath(path);
        nodeById.put(node.id, node);

        nodes.add(node);
        byType.computeIfAbsent(type, k -> new ArrayList<>(2)).add(node.id);

        if (node.parentId != -1) {
            nodeById.get(node.parentId).children.add(node.id);
        }

        stack.addLast(node.id);
        return node.id;
    }

    /**
     * Exit the current indexed node if one is active.
     */
    public void onExit() {
        if (stack.isEmpty()) {
            return;
        }
        int id = stack.removeLast();
        MsgNode node = nodes.get(id);
        node.exit = ++time;
    }

    public List<MsgNode> nodes() {
        return nodes;
    }

    public List<Integer> nodesByType(MsgType type) {
        return byType.getOrDefault(type, new ArrayList<>());
    }

    public MsgNode node(int id) {
        return nodeById.get(id);
    }

    public String pathOf(int pathId) {
        return pathTable.get(pathId);
    }

    public boolean contains(int ancestorId, int childId) {
        MsgNode ancestor = nodeById.get(ancestorId);
        MsgNode child = nodeById.get(childId);
        return ancestor.enter <= child.enter && child.exit <= ancestor.exit;
    }

    /**
     * Return the direct children of the synthetic packet-root node.
     *
     * @return root-level business nodes for the chain
     */
    public List<Integer> roots() {
        List<Integer> packetRoots = nodesByType(MsgType.PACKET);
        if (packetRoots.isEmpty()) {
            return List.of();
        }
        MsgNode root = nodeById.get(packetRoots.get(0));
        return root.children;
    }

    private int internPath(String path) {
        String normalized = path == null ? "" : path;
        Integer id = pathToId.get(normalized);
        if (id != null) {
            return id;
        }
        int newId = pathTable.size();
        pathTable.add(normalized);
        pathToId.put(normalized, newId);
        return newId;
    }

    public boolean hasTypeByMap(MsgType type) {
        ArrayList<Integer> ids = byType.get(type);
        return ids != null && !ids.isEmpty();
    }

    public boolean hasType(MsgType type) {
        return (typeMask & (1L << type.ordinal())) != 0;
    }

    public List<Integer> nodeIdsOf(MsgType type) {
        ArrayList<Integer> ids = byType.get(type);
        return ids == null ? List.of() : ids;
    }

    public int firstNodeIdOf(MsgType type) {
        ArrayList<Integer> ids = byType.get(type);
        return (ids == null || ids.isEmpty()) ? -1 : ids.get(0);
    }

    public List<MsgNode> nodesOf(MsgType type) {
        ArrayList<Integer> ids = byType.get(type);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        ArrayList<MsgNode> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            out.add(nodeById.get(id));
        }
        return out;
    }
}
