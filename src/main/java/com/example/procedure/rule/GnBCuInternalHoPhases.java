package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;

import java.util.*;

/**
 * gNB-CU 内部切换阶段定义（按流程图：一条主要消息=一个阶段）
 */
public final class GnBCuInternalHoPhases {

    private GnBCuInternalHoPhases() {}

    public enum StartType {
        NOT_START,
        CONFIRMED_START
    }

    /**
     * 0  Uu: HandoverCommand/RRCReconfiguration
     * 1  Uu: RRCReconfigurationComplete（成功结束）
     * 2  Uu: RRCReestablishmentRequest（失败结束）
     */
    public static final int PH_UU_HO_CMD_OR_RRC_RECFG = 0;
    public static final int PH_UU_RRC_RECFG_COMPLETE = 1;
    public static final int PH_UU_RRC_REEST_REQ = 2;

    private static final Map<String, PhaseDef.PhaseLocation> LOCATIONS;

    static {
        Map<String, PhaseDef.PhaseLocation> m = new HashMap<>();

        add(m, "HANDOVERCOMMAND/RRCRECONFIGURATION",
                new PhaseDef.PhaseLocation(PH_UU_HO_CMD_OR_RRC_RECFG, 0, true, true));

        add(m, "RRCRECONFIGURATIONCOMPLETE",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RECFG_COMPLETE, 0, true, true));

        add(m, "RRCREESTABLISHMENTREQUEST",
                new PhaseDef.PhaseLocation(PH_UU_RRC_REEST_REQ, 0, true, true));

        LOCATIONS = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, PhaseDef.PhaseLocation> m, String msgType, PhaseDef.PhaseLocation loc) {
        m.put(normalize(msgType), loc);
    }

    public static PhaseDef.PhaseLocation locate(String msgType) {
        if (msgType == null) return null;
        return LOCATIONS.get(normalize(msgType));
    }

    /**
     * 结束类消息：
     * - 成功：RRCReconfigurationComplete
     * - 失败：RRCReestablishmentRequest
     */
    public static boolean isEndMessage(String msgType) {
        if (msgType == null) return false;
        String u = normalize(msgType);
        return u.equals("RRCRECONFIGURATIONCOMPLETE")
                || u.equals("RRCREESTABLISHMENTREQUEST");
    }

    /**
     * 起始判定：入口消息即确认为开始（强锚点）
     */
    public static StartType checkStartType(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return StartType.NOT_START;
        if (normalize(msg.getMsgType()).equals("HANDOVERCOMMAND/RRCRECONFIGURATION")) {
            return StartType.CONFIRMED_START;
        }
        return StartType.NOT_START;
    }

    /**
     * payload 校验占位：你图里要求解析 MasterKeyUpdate/keySetChangeIndicator/算法/PCI 等
     * 先默认 true，让流程跑通；后续你再收紧校验。
     */
    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        return true;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
