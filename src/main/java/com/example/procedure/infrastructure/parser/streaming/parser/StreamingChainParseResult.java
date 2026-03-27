package com.example.procedure.infrastructure.parser.streaming.parser;

import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.example.procedure.infrastructure.parser.streaming.index.ChainIndex;
import com.example.procedure.infrastructure.parser.streaming.index.MsgNode;
import com.example.procedure.infrastructure.parser.streaming.index.MsgType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * Normalized result of parsing one streaming chain from tshark JSON.
 *
 * A single packet may yield multiple chain results when it contains multiple
 * wanted logical protocol paths.
 */
public class StreamingChainParseResult {
    private String ueId;   // 閫氬父浠?NUAR 鍛戒腑鏃跺～ imsi

    private int msgCode = 0; // 0=UNKNOWN


    // 缁熶竴閮界敤 list锛氬悓涓€閾鹃噷鐞嗚涓婂彲鑳藉娆″嚭鐜帮紙鍗充娇姒傜巼灏忥級
    private List<MacInfo> macList = new ArrayList<>();
    private List<PdcpInfo> pdcpList = new ArrayList<>();
    private List<RrcInfo> rrcList = new ArrayList<>();

    // NAS/NGAP 鏈潵灏卞彲鑳藉娆?
    private List<NasInfo> nasList = new ArrayList<>();
    private List<NgapInfo> ngapList = new ArrayList<>();

    // http2:json 鍦烘櫙涓€鑸氨涓€鏉★紝浣嗕篃鍙墿灞曟垚 list锛涜繖閲屼繚鐣欏崟涓?
    private NUARInfo nuarInfo;

    private Boolean encrypted = false;
    private String encryptedType = "NONE"; // NONE / NAS / PDCP / NAS+PDCP

    // ===== frame (姣忔潯 chain 閮藉甫涓€浠藉悓鍖?frame 淇℃伅) =====
    private long frameNo;            // frame.number
    private long timestampMs;        // frame.time_epoch * 1000
    private String frameProtocols;   // frame.protocols 鍘熷涓?
    private List<String> protoList = new ArrayList<>(); // split(":") 鍚?

    private String iface = "UNKNOWN";
    private String direction = "UNKNOWN";   // UL / DL / UNKNOWN

    private ChainIndex index;

    public List<?> getObjectsOf(MsgType type) {
        ChainIndex idx = getIndex();
        if (idx == null) return List.of();

        List<Integer> nodeIds = idx.nodeIdsOf(type);
        if (nodeIds.isEmpty()) return List.of();

        switch (type) {
            case MAC:  return pickFromList(idx, nodeIds, macList);
            case PDCP: return pickFromList(idx, nodeIds, pdcpList);
            case RRC:  return pickFromList(idx, nodeIds, rrcList);
            case NAS:  return pickFromList(idx, nodeIds, nasList);
            case NGAP: return pickFromList(idx, nodeIds, ngapList);
            case NUAR: return (nuarInfo == null) ? List.of() : List.of(nuarInfo);
            default:   return List.of();
        }
    }

    private static <T> List<T> pickFromList(ChainIndex idx, List<Integer> nodeIds, List<T> list) {
        List<T> out = new ArrayList<>(nodeIds.size());
        for (int nodeId : nodeIds) {
            MsgNode n = idx.node(nodeId);
            int i = n.payloadIndex;
            if (i >= 0 && i < list.size()) {
                out.add(list.get(i));
            }
        }
        return out;
    }

    public static String normalizedMsgType(int code) {
        // 浣犲浐瀹?code 鐨勫畾涔夛細绀轰緥鎸夋垜涔嬪墠缁欑殑
        switch (code) {
            case 1001: return "RRCSetupComplete";
            case 1002: return "RRC SecurityModeCommand";
            case 2001: return "Initial UE Message";
            case 2002: return "Initial Context Setup Request";
            case 3001: return "NAS SecurityModeCommand";
            case 4001: return "Nausf_UEAuthentication_Authenticate Response";
            default: return null;
        }
    }


}
