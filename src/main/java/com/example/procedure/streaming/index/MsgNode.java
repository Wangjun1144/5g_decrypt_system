package com.example.procedure.streaming.index;

import java.util.ArrayList;
import java.util.List;

public final class MsgNode {
    public int id;
    public MsgType type;

    public int enter;
    public int exit;
    public int depth;

    public int parentId;        // -1 if none
    public List<Integer> children = new ArrayList<>(2);

    public int payloadIndex;    // 对应 result 中 list 的下标；PACKET 用 -1
    public int pathId;          // 可选：路径表 id
}
