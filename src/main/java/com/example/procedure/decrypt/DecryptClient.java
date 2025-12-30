package com.example.procedure.decrypt;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DecryptClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 请求参数对象（字段名会按 JSON key 输出） */
    public static class DecryptRequest {
        public String messageId;
        public String ueId;
        public String contextRef;
        public String layer;

        public String encKey;
        public String intKey;
        public String encAlgo;
        public String intAlgo;

        public int count;
        public int bearer;
        public String direction;   // "UL" / "DL"

        public String ciphertext;  // hex
        public String mac;         // hex, 支持 "0x..." 或纯 hex
        public int dataLength;
    }

    /**
     * 调用解密服务：POST /decrypt
     * @param urlStr 例如 "http://127.0.0.1:8004/decrypt"
     * @param req    请求参数对象
     * @return       响应 body（JSON 字符串）
     */
    public static String decrypt(String urlStr, DecryptRequest req) throws Exception {
        String json = MAPPER.writeValueAsString(req);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        // send body
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        // read response
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder respSb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                respSb.append(line);
            }
        } finally {
            conn.disconnect();
        }

        // 你如果希望非 2xx 直接抛异常，打开下面这段：
        // if (status < 200 || status >= 300) {
        //     throw new RuntimeException("HTTP " + status + ": " + respSb);
        // }

        return respSb.toString();
    }

}
