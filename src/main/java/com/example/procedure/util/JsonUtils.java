package com.example.procedure.util;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {

    /**
     * 按 key 路径逐层向下取，取不到就返回 null
     */
    public static JsonNode path(JsonNode node, String... keys) {
        JsonNode cur = node;
        for (String k : keys) {
            if (cur == null || cur.isMissingNode()) {
                return null;
            }
            cur = cur.get(k);
        }
        return cur;
    }

    /**
     * 取 text，如果 node 是 null 或 missing 就返回默认值
     */
    public static String text(JsonNode node, String defaultValue) {
        return (node == null || node.isMissingNode()) ? defaultValue : node.asText();
    }

    /**
     * 简单判断某条路径是否存在
     */
    public static boolean hasPath(JsonNode node, String... keys) {
        return path(node, keys) != null;
    }
}
