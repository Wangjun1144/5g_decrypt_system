package com.example.procedure.streaming.parser;

import com.example.procedure.parser.*;
import com.example.procedure.streaming.index.ChainIndex;
import com.example.procedure.streaming.index.MsgNode;
import com.example.procedure.streaming.index.MsgType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RrcNasParseResult {
    private String ueId;   // 通常仅 NUAR 命中时填 imsi

    private int msgCode = 0; // 0=UNKNOWN


    // 统一都用 list：同一链里理论上可能多次出现（即使概率小）
    private List<MacInfo> macList = new ArrayList<>();
    private List<PdcpInfo> pdcpList = new ArrayList<>();
    private List<RrcInfo> rrcList = new ArrayList<>();

    // NAS/NGAP 本来就可能多次
    private List<NasInfo> nasList = new ArrayList<>();
    private List<NgapInfo> ngapList = new ArrayList<>();

    // http2:json 场景一般就一条，但也可扩展成 list；这里保留单个
    private NUARInfo nuarInfo;

    private Boolean encrypted = false;
    private String encryptedType = "NONE"; // NONE / NAS / PDCP / NAS+PDCP

    // ===== frame (每条 chain 都带一份同包 frame 信息) =====
    private long frameNo;            // frame.number
    private long timestampMs;        // frame.time_epoch * 1000
    private String frameProtocols;   // frame.protocols 原始串
    private List<String> protoList = new ArrayList<>(); // split(":") 后

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
        // 你固定 code 的定义：示例按我之前给的
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
