package com.example.procedure.infrastructure.decrypt.gateway.http;

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

    /** 璇锋眰鍙傛暟瀵硅薄锛堝瓧娈靛悕浼氭寜 JSON key 杈撳嚭锛?*/
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
        public String mac;         // hex, 鏀寔 "0x..." 鎴栫函 hex
        public int dataLength;
    }

    /**
     * 璋冪敤瑙ｅ瘑鏈嶅姟锛歅OST /decrypt
     * @param urlStr 渚嬪 "http://127.0.0.1:8004/decrypt"
     * @param req    璇锋眰鍙傛暟瀵硅薄
     * @return       鍝嶅簲 body锛圝SON 瀛楃涓诧級
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

        // 浣犲鏋滃笇鏈涢潪 2xx 鐩存帴鎶涘紓甯革紝鎵撳紑涓嬮潰杩欐锛?
        // if (status < 200 || status >= 300) {
        //     throw new RuntimeException("HTTP " + status + ": " + respSb);
        // }

        return respSb.toString();
    }

}
