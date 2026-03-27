package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.infrastructure.decode.bridge.plaintext.debug.DebugDecodeArtifacts;
import com.example.procedure.infrastructure.decode.bridge.plaintext.PlaintextDecodeBridgeService;
import com.example.procedure.infrastructure.decode.bridge.plaintext.PlaintextDecodeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
class PlaintextDecodeBridgeIT {

    @Autowired
    private PlaintextDecodeBridgeService bridgeService;

    @Test
    void debug_build_rrc_pcap_and_json() throws Exception {
        PlaintextDecodeRequest req = new PlaintextDecodeRequest();
        req.setPlainHex("7e005e7700098596610526864009f07100387e004119000bf200f110020040c00007ec1001032e04f070f0702f0201015200f1100000641707f070c0401180b018010074000090530103");
        req.setProtocolHint("NAS_5GS");
        req.setTraceId("trace002");
        req.setSourceMsgId("msg002");
        req.setUeId("UE_TEST");

        DebugDecodeArtifacts out = bridgeService.buildDebugArtifacts(req);

        System.out.println("=== Debug decode done ===");
        System.out.println("workDir : " + out.getWorkDir().toAbsolutePath());
        System.out.println("hexdump : " + (out.getHexdumpFile() == null ? "deleted" : out.getHexdumpFile().toAbsolutePath()));
        System.out.println("pcap    : " + out.getPcapFile().toAbsolutePath());
        System.out.println("json    : " + out.getJsonFile().toAbsolutePath());
        System.out.println("dlt     : " + out.getDlt());
        System.out.println("bytes   : " + out.getByteLength());
    }
}
