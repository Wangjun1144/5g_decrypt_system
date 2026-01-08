package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;

import java.util.*;

/**
 * RRC 重建流程阶段定义（严格按你给的流程图）
 */
public final class RrcReestablishmentPhases {

    private RrcReestablishmentPhases() {}

    // ============== StartType ==============

    public enum StartType {
        NOT_START,
        CONFIRMED_START
    }

    // ============== PhaseIndex（按流程顺序） ==============

    /**
     * 0  Uu: RRCReestablishmentRequest
     * 1  Xn: Retrieve UE Context Request
     * 2  Xn: Retrieve UE Context Response
     * 3  Uu: RRCReestablishment
     * 4  Uu: RRCReestablishmentComplete
     */
    public static final int PH_RRC_REEST_REQ = 0;
    public static final int PH_XN_RETRIEVE_CTX_REQ = 1;
    public static final int PH_XN_RETRIEVE_CTX_RESP = 2;
    public static final int PH_RRC_REEST = 3;
    public static final int PH_RRC_REEST_COMPLETE = 4;

    // ============== msgType -> PhaseLocation ==============

    private static final Map<String, PhaseDef.PhaseLocation> LOCATIONS;

    static {
        Map<String, PhaseDef.PhaseLocation> m = new HashMap<>();

        add(m, "RRCREESTABLISHMENTREQUEST",
                new PhaseDef.PhaseLocation(PH_RRC_REEST_REQ, 0, true, true));

        add(m, "RETRIEVE UE CONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_XN_RETRIEVE_CTX_REQ, 0, true, true));

        add(m, "RETRIEVE UE CONTEXT RESPONSE",
                new PhaseDef.PhaseLocation(PH_XN_RETRIEVE_CTX_RESP, 0, true, true));

        add(m, "RRCREESTABLISHMENT",
                new PhaseDef.PhaseLocation(PH_RRC_REEST, 0, true, true));

        add(m, "RRCREESTABLISHMENTCOMPLETE",
                new PhaseDef.PhaseLocation(PH_RRC_REEST_COMPLETE, 0, true, true));

        LOCATIONS = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, PhaseDef.PhaseLocation> m,
                            String msgType,
                            PhaseDef.PhaseLocation loc) {
        m.put(normalize(msgType), loc);
    }

    public static PhaseDef.PhaseLocation locate(String msgType) {
        if (msgType == null) return null;
        return LOCATIONS.get(normalize(msgType));
    }

    // ============== end message ==============

    public static boolean isEndMessage(String msgType) {
        if (msgType == null) return false;
        return normalize(msgType).equals("RRCREESTABLISHMENTCOMPLETE");
    }

    // ============== start 判定 ==============

    public static StartType checkStartType(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return StartType.NOT_START;
        if (normalize(msg.getMsgType()).equals("RRCREESTABLISHMENTREQUEST")) {
            return StartType.CONFIRMED_START;
        }
        return StartType.NOT_START;
    }

    // ============== payload 校验占位 ==============

    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        // 后续可补：
        // - C-RNTI
        // - UE Context ID
        // - NCC / KNG-RAN
        return true;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
