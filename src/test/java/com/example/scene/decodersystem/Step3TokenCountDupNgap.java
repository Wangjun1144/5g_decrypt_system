package com.example.scene.decodersystem;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Step3TokenCountDupNgap {

    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Path jsonPath = Path.of(args.length > 0 ? args[0] : "test.json");

        try (InputStream in = Files.newInputStream(jsonPath)) {
            JsonFactory factory = MAPPER.getFactory();
            try (JsonParser p = factory.createParser(in)) {

                // 顶层是数组
                if (p.nextToken() != JsonToken.START_ARRAY) {
                    throw new RuntimeException("Expected top-level array");
                }

                // 读第一个 packet 对象
                if (p.nextToken() != JsonToken.START_OBJECT) {
                    throw new RuntimeException("Expected first element object");
                }

                int ngapCount = countNgapInLayersOfFirstPacket(p);
                System.out.println("TOKEN-LEVEL ngap count under _source.layers = " + ngapCount);
            }
        }
    }

    // 解析第一个 packet 对象（从 START_OBJECT 开始），找到 _source.layers，然后统计 layers 里 FIELD_NAME=="ngap" 出现次数
    static int countNgapInLayersOfFirstPacket(JsonParser p) throws Exception {
        int ngapCount = 0;

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String field = p.currentName();
            JsonToken valueToken = p.nextToken();

            if ("_source".equals(field) && valueToken == JsonToken.START_OBJECT) {
                ngapCount += countNgapInsideSource(p);
            } else {
                p.skipChildren();
            }
        }
        return ngapCount;
    }

    static int countNgapInsideSource(JsonParser p) throws Exception {
        int ngapCount = 0;

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String field = p.currentName();
            JsonToken valueToken = p.nextToken();

            if ("layers".equals(field) && valueToken == JsonToken.START_OBJECT) {
                ngapCount += countNgapInsideLayersObject(p);
            } else {
                p.skipChildren();
            }
        }
        return ngapCount;
    }

    static int countNgapInsideLayersObject(JsonParser p) throws Exception {
        int ngapCount = 0;

        // 现在在 layers 的 START_OBJECT 内部，逐字段读
        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String name = p.currentName();
            p.nextToken(); // move to value token

            if ("ngap".equals(name)) {
                ngapCount++;
            }

            // 不需要读 value，直接跳过整个 value 子树
            p.skipChildren();
        }
        return ngapCount;
    }
}
