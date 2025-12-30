package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest(classes = Application.class)
public class TsharkJsonMessageParserTest {

    /**
     * 只解析 gNB 侧的 json（gnb_capture.json），
     * 输出每条信令的基础信息。
     */
    @Test
    void testParseSingleGnbJson() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        String gnbPath  = "gnb_capture.json";
        String gnbPath_raw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePath_raw = "5g_srsRAN_n78_gain40_amf_raw.json";

        List<SignalingMessage> messages = parser.parseAndMerge(gnbPath, corePath,
                gnbPath_raw, corePath_raw);

        System.out.println("===== GNB JSON ONLY =====");
        for (SignalingMessage m : messages) {
            // 基本信息
            System.out.printf("frame=%d time=%d iface=%s dir=%s layer=%s type=%s messageid=%s ueid=%s%n",
                    m.getFrameNo(),
                    m.getTimestamp(),
                    m.getIface(),
                    m.getDirection(),
                    m.getProtocolLayer(),
                    m.getMsgType(),
                    m.getMsgId(),
                    m.getUeId()
            );

            // ================== MAC 信息 ==================
            MacInfo mac = m.getMacInfo();
            if (mac != null) {
                System.out.println("  [MAC]");
                System.out.printf("    rnti=%s, rntiType=%s%n",
                        mac.getRnti(),
                        mac.getRntiType());
            }

            // ================== PDCP 信息 ==================
            PdcpInfo pdcp = m.getPdcpInfo();
            if (pdcp != null) {
                System.out.println("  [PDCP]");
                System.out.printf("    signallingDataHex=%s%n",
                        shortHex(pdcp.getSignallingDataHex(), 80));
                System.out.printf("    macHex=%s%n",
                        shortHex(pdcp.getMacHex(), 80));
            }

            // ================== RRC 信息 ==================
            RrcInfo rrc = m.getRrcInfo();
            if (rrc != null) {
                System.out.println("  [RRC]");
                System.out.printf("    direction=%s, msgName=%s%n",
                        rrc.getDirection(),
                        rrc.getMsgName());
                System.out.printf("    integrityProtAlgorithm=%s, cipheringAlgorithm=%s, crnti=%s, hasDedicatedNas=%s%n",
                        rrc.getIntegrityProtAlgorithm(),
                        rrc.getCipheringAlgorithm(),
                        rrc.getCrnti(),
                        rrc.isHasDedicatedNas());
            }

            // ================== NGAP 信息 ==================
            if (m.getNgapInfoList() == null || m.getNgapInfoList().isEmpty()) {
                System.out.println("  [NGAP] count = 0");
            } else {
                System.out.println("  [NGAP] count = " + m.getNasList().size());
                int idx = 0;
                for(NgapInfo ngap : m.getNgapInfoList()){
                    System.out.println("  [NGAP]");
                    System.out.printf("    pduType=%s, msgName=%s%n",
                            ngap.getPduType(),
                            ngap.getMsgName());
                    System.out.printf("    securityKeyHex=%s%n",
                            shortHex(ngap.getSecurityKeyHex(), 80));
                    System.out.printf("    ranUeNgapId=%s%n",
                            ngap.getRanUeNgapId());
                }

            }

            // ================== NAUSF (http2:json) 信息 ==================
            NUARInfo nuar = m.getNuarInfo();
            if (nuar != null) {
                System.out.println("  [NAUSF]");
                System.out.printf("    msgName=%s%n", nuar.getMsgName());
                System.out.printf("    supi=%s, imsi=%s%n",
                        nuar.getSupi(),
                        nuar.getImsi());
                System.out.printf("    authResult=%s%n",
                        nuar.getAuthResult());
                System.out.printf("    kseaf=%s%n",
                        shortHex(nuar.getKseafHex(), 80));
            }

            // ================== NAS 信息 ==================
            if (m.getNasList() == null || m.getNasList().isEmpty()) {
                System.out.println("  [NAS] count = 0");
            } else {
                System.out.println("  [NAS] count = " + m.getNasList().size());
                int idx = 0;
                for (NasInfo nas : m.getNasList()) {
                    String cipherHex = nas.getCipherTextHex();
                    String shortCipher = shortHex(cipherHex, 80);

                    System.out.printf(
                            "    NAS[%d]: encrypted=%s, secHdrType=%s, mmType=%s%n",
                            idx,
                            nas.isEncrypted(),
                            nas.getSecurityHeaderType(),
                            nas.getMmMessageType()
                    );

                    System.out.printf(
                            "            encAlgo=%s, intAlgo=%s%n",
                            nas.getNas_integrityProtAlgorithm(),
                            nas.getNas_cipheringAlgorithm()
                    );

                    System.out.printf(
                            "            GUAMI(mcc=%s, mnc=%s), TMSI=%s, regType5gs=%s%n",
                            nas.getGuamiMcc(),
                            nas.getGuamiMnc(),
                            nas.getTmsi(),
                            nas.getRegType5gs()
                    );

                    System.out.printf(
                            "            cipherTextHex=%s%n",
                            shortCipher
                    );

                    idx++;
                }
            }

            System.out.println("--------------------------------------------------");
        }
    }


    private static String shortHex(String hex, int maxLen) {
        if (hex == null) return "null";
        if (hex.length() <= maxLen) return hex;
        return hex.substring(0, maxLen) + "...";
    }


    /**
     * 同时解析 gNB + AMF 两个 json，
     * 使用 parseAndMerge 按 timestamp + frameNo 排好时间顺序，
     * 方便你观察完整的时序流程。
     */
    @Test
    void testParseAndMergeGnbAndCore() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        // 按你自己的文件实际路径改
        String gnbPath  = "gnb_capture.json";
        String gnbPath_raw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePath_raw = "5g_srsRAN_n78_gain40_amf.json";

        List<SignalingMessage> messages = parser.parseAndMerge(gnbPath, gnbPath_raw,
                corePath, corePath_raw);

        System.out.println("===== MERGED GNB + CORE JSON =====");
        for (SignalingMessage m : messages) {
            System.out.printf("frame=%d time=%d iface=%s dir=%s layer=%s type=%s%n",
                    m.getFrameNo(),
                    m.getTimestamp(),
                    m.getIface(),
                    m.getDirection(),
                    m.getProtocolLayer(),
                    m.getMsgType());
        }
    }
}

