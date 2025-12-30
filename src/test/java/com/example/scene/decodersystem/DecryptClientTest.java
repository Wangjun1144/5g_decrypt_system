package com.example.scene.decodersystem;

import com.example.procedure.decrypt.DecryptClient;
import org.junit.jupiter.api.Test;

public class DecryptClientTest {

    @Test
    void decrypt_shouldReturnResponse() throws Exception {
        String url = "http://127.0.0.1:8004/decrypt";

        DecryptClient.DecryptRequest req = new DecryptClient.DecryptRequest();
        req.messageId = "MSG001";
        req.ueId = "UE1";
        req.contextRef = "CTX01";
        req.layer = "AS";
        req.encKey = "B99435D4281E96506F0D3C80F2916A317AE24EF8F3C67FFD70D1CE670DB22335";
        req.intKey = "FDDB62C3BD352D20DC548F18F46FE8D32A4A2008C4463FFA21B1ECC696FDEB0F";
        req.encAlgo = "NEA2";
        req.intAlgo = "NIA2";
        req.count = 3;
        req.bearer = 0;
        req.direction = "DL";
        req.ciphertext = "705fec44d464a411";
        req.mac = "0xda7ed22d";
        req.dataLength = 0;

        String resp = DecryptClient.decrypt(url, req);
        System.out.println(resp);

        // 可选断言：至少不是空
        // org.junit.jupiter.api.Assertions.assertNotNull(resp);
        // org.junit.jupiter.api.Assertions.assertFalse(resp.trim().isEmpty());
    }
}

