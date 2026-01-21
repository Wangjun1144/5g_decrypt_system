package com.example.scene.decodersystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class Step2TreeModelLosesDup {
    public static void main(String[] args) throws Exception{
        Path jsonPath = Path.of(args.length > 0 ? args[0] : "test.json");
        ObjectMapper mapper = new ObjectMapper();
        String text = Files.readString(jsonPath);

        // 1) 整个文件读成树（注意：顶层是数组）
        JsonNode root = mapper.readTree(text);


        // 2) 取第一个 packet
        JsonNode packet0 = root.get(0);
        if (packet0 == null) {
            System.out.println("No packet found (root[0] is null).");
            return;
        }

        // 3) 定位到 _source.layers
        JsonNode layers = packet0.at("/_source/layers");
        if (layers.isMissingNode() || layers.isNull()) {
            System.out.println("Cannot find _source.layers, actual packet keys:");
            System.out.println(packet0.fieldNames().hasNext() ? packet0.fieldNames().next() : "(empty)");
            return;
        }

        // 4) 直接 get("ngap") —— 这里就是“必然失败”的点：只会得到一个
        JsonNode ngap = layers.get("ngap");

        System.out.println("layers has ngap? " + (ngap != null && !ngap.isNull()));

        if (ngap != null) {
            // 打印一个摘要：ngap 节点类型 + 字段数量（如果是对象）
            System.out.println("ngap node type = " + ngap.getNodeType());
            if (ngap.isObject()) {
                System.out.println("ngap field count = " + ngap.size());
            } else if (ngap.isArray()) {
                System.out.println("ngap array size = " + ngap.size());
            }

            // 打印 ngap 的前 300 个字符（避免太长）
            String ngapStr = ngap.toString();
            System.out.println("ngap json snippet = " + ngapStr.substring(0, Math.min(300, ngapStr.length())));
        }

        // 5) 对照：用“文本计数”看 layers 里可能有几个 "ngap"
        // （这只是对照用，不严谨但足够证明“文件里确实出现多次”）
        int occurrences = countOccurrences(text, "\"ngap\"");
        System.out.println("Raw text occurrences of \"ngap\" = " + occurrences);

        System.out.println("\nNOTE: Even if the raw text shows multiple \"ngap\" keys, layers.get(\"ngap\") returns only one.");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

}
