package com.example.procedure.streaming.index;

import java.util.*;

public final class ChainIndex {

    private int time = 0;
    private long typeMask = 0L;
    private final ArrayList<MsgNode> nodes = new ArrayList<>(16);
    private final ArrayDeque<Integer> stack = new ArrayDeque<>(16);

    private final EnumMap<MsgType, ArrayList<Integer>> byType = new EnumMap<>(MsgType.class);

    private final ArrayList<String> pathTable = new ArrayList<>(32);
    private final HashMap<String, Integer> pathToId = new HashMap<>(64);

    public void startPacketRoot(String rootPath, int depth) {
        // 建 PACKET 虚拟根，并压栈
        onEnter(MsgType.PACKET, depth, rootPath, -1);
    }

    public void endPacketRoot() {
        // 关闭 PACKET 根（栈顶应该就是 PACKET）
        onExit();
    }

    public int onEnter(MsgType type, int depth, String path, int payloadIndex) {
        typeMask |= (1L << type.ordinal());
        MsgNode n = new MsgNode();
        n.id = nodes.size();
        n.type = type;
        n.depth = depth;
        n.enter = ++time;
        n.exit = -1;
        n.parentId = stack.isEmpty() ? -1 : stack.peekLast();
        n.payloadIndex = payloadIndex;
        n.pathId = internPath(path);

        nodes.add(n);
        byType.computeIfAbsent(type, k -> new ArrayList<>(2)).add(n.id);

        if (n.parentId != -1) {
            nodes.get(n.parentId).children.add(n.id);
        }

        stack.addLast(n.id);
        return n.id;
    }

    public void onExit() {
        if (stack.isEmpty()) return;
        int id = stack.removeLast();
        MsgNode n = nodes.get(id);
        n.exit = ++time;
    }

    public List<MsgNode> nodes() { return nodes; }

    public List<Integer> nodesByType(MsgType type) {
        return byType.getOrDefault(type, new ArrayList<>());
    }

    public MsgNode node(int id) { return nodes.get(id); }

    public String pathOf(int pathId) { return pathTable.get(pathId); }

    public boolean contains(int aId, int bId) {
        MsgNode a = nodes.get(aId);
        MsgNode b = nodes.get(bId);
        return a.enter <= b.enter && b.exit <= a.exit;
    }

    public List<Integer> roots() {
        // 在 PACKET 虚拟根模式下，roots 就是 PACKET 的 children
        List<Integer> pack = nodesByType(MsgType.PACKET);
        if (pack.isEmpty()) return List.of();
        MsgNode root = nodes.get(pack.get(0));
        return root.children;
    }

    private int internPath(String path) {
        if (path == null) path = "";
        Integer id = pathToId.get(path);
        if (id != null) return id;
        int newId = pathTable.size();
        pathTable.add(path);
        pathToId.put(path, newId);
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
        if (ids == null || ids.isEmpty()) return List.of();

        ArrayList<MsgNode> out = new ArrayList<>(ids.size());
        for (int id : ids) out.add(nodes.get(id));
        return out;
    }



}
