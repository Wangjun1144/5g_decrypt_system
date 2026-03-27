package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.infrastructure.parser.TsharkJsonMessageParser;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

/**
 * Integration-style checks for the file-oriented tshark JSON parser.
 *
 * These tests still matter even though the long-term mainline parsing path is
 * streaming-based, because the file parser remains useful for offline
 * comparison, regression checks, and exploratory decode workflows.
 */
@SpringBootTest(classes = Application.class)
public class TsharkJsonMessageParserTest {

    /**
     * Parse one pair of gNB/core captures and print the merged message view.
     */
    @Test
    void testParseSingleGnbJson() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        String gnbPath = "gnb_capture.json";
        String gnbPathRaw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePathRaw = "5g_srsRAN_n78_gain40_amf_raw.json";

        List<SignalingMessage> messages = parser.parseAndMerge(
                gnbPath,
                corePath,
                gnbPathRaw,
                corePathRaw
        );

        System.out.println("===== GNB JSON ONLY =====");
        for (SignalingMessage m : messages) {
            System.out.printf(
                    "frame=%d time=%d iface=%s dir=%s layer=%s type=%s messageid=%s ueid=%s%n",
                    m.getFrameNo(),
                    m.getTimestamp(),
                    m.getIface(),
                    m.getDirection(),
                    m.getProtocolLayer(),
                    m.getMsgType(),
                    m.getMsgId(),
                    m.getUeId()
            );

            MacInfo mac = m.getMacInfo();
            if (mac != null) {
                System.out.println("  [MAC]");
                System.out.printf("    rnti=%s, rntiType=%s%n", mac.getRnti(), mac.getRntiType());
            }

            PdcpInfo pdcp = m.getPdcpInfo();
            if (pdcp != null) {
                System.out.println("  [PDCP]");
                System.out.printf(
                        "    direction=%s, bearerType=%s, bearerName=%s, seqNum=%s, encrypted=%s%n",
                        pdcp.getDirection(),
                        pdcp.getBearerType(),
                        pdcp.getBearerName(),
                        pdcp.getSeqnum(),
                        pdcp.isPdcpencrypted()
                );
                System.out.printf(
                        "    signallingDataHex=%s, macHex=%s%n",
                        pdcp.getSignallingDataHex(),
                        pdcp.getMacHex()
                );
            }

            RrcInfo rrc = m.getRrcInfo();
            if (rrc != null) {
                System.out.println("  [RRC]");
                System.out.printf(
                        "    msgName=%s, direction=%s, crnti=%s, hasDedicatedNas=%s%n",
                        rrc.getMsgName(),
                        rrc.getDirection(),
                        rrc.getCrnti(),
                        rrc.isHasDedicatedNas()
                );
                System.out.printf(
                        "    cipherAlg=%s, intAlg=%s%n",
                        rrc.getCipheringAlgorithm(),
                        rrc.getIntegrityProtAlgorithm()
                );
            }

            List<NasInfo> nasList = m.getNasList();
            if (nasList != null && !nasList.isEmpty()) {
                System.out.println("  [NAS list]");
                for (int i = 0; i < nasList.size(); i++) {
                    NasInfo nas = nasList.get(i);
                    System.out.printf(
                            "    #%d mmType=%s enc=%s fullNas=%s cipher=%s mac=%s seq=%d%n",
                            i,
                            nas.getMmMessageType(),
                            nas.isEncrypted(),
                            nas.getFullNasPduHex(),
                            nas.getCipherTextHex(),
                            nas.getMsgAuthCodeHex(),
                            nas.getSeqNoInt()
                    );
                    System.out.printf(
                            "       guamiMcc=%s guamiMnc=%s tmsi=%s regType=%s%n",
                            nas.getGuamiMcc(),
                            nas.getGuamiMnc(),
                            nas.getTmsi(),
                            nas.getRegType5gs()
                    );
                    System.out.printf(
                            "       nasCipherAlg=%s nasIntAlg=%s%n",
                            nas.getNas_cipheringAlgorithm(),
                            nas.getNas_integrityProtAlgorithm()
                    );
                }
            }

            List<NgapInfo> ngapList = m.getNgapInfoList();
            if (ngapList != null && !ngapList.isEmpty()) {
                System.out.println("  [NGAP list]");
                for (int i = 0; i < ngapList.size(); i++) {
                    NgapInfo ngap = ngapList.get(i);
                    System.out.printf(
                            "    #%d msgName=%s pduType=%s direction=%s ranUeNgapId=%s securityKey=%s%n",
                            i,
                            ngap.getMsgName(),
                            ngap.getPduType(),
                            ngap.getDirection(),
                            ngap.getRanUeNgapId(),
                            ngap.getSecurityKeyHex()
                    );
                }
            }

            NUARInfo nuar = m.getNuarInfo();
            if (nuar != null) {
                System.out.println("  [NUAR]");
                System.out.printf(
                        "    msgName=%s, supi=%s, imsi=%s, kseaf=%s, authResult=%s%n",
                        nuar.getMsgName(),
                        nuar.getSupi(),
                        nuar.getImsi(),
                        nuar.getKseafHex(),
                        nuar.getAuthResult()
                );
            }

            System.out.println();
        }
    }

    /**
     * Parse two files without pinning reordering and print the normalized
     * merged order for inspection.
     */
    @Test
    void testParseAndMergeNoPin() throws IOException {
        TsharkJsonMessageParser parser = new TsharkJsonMessageParser();

        String gnbPath = "gnb_capture.json";
        String gnbPathRaw = "gnb_capture_raw.json";
        String corePath = "5g_srsRAN_n78_gain40_amf.json";
        String corePathRaw = "5g_srsRAN_n78_gain40_amf_raw.json";

        List<SignalingMessage> messages = parser.parseAndMergeNoPin(
                gnbPath,
                corePath,
                gnbPathRaw,
                corePathRaw
        );

        System.out.println("===== MERGED NO PIN =====");
        for (SignalingMessage m : messages) {
            System.out.printf(
                    "frame=%d time=%d iface=%s dir=%s layer=%s type=%s msgId=%s ueId=%s%n",
                    m.getFrameNo(),
                    m.getTimestamp(),
                    m.getIface(),
                    m.getDirection(),
                    m.getProtocolLayer(),
                    m.getMsgType(),
                    m.getMsgId(),
                    m.getUeId()
            );
        }
    }
}
