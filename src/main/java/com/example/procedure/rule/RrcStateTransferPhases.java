package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;

import java.util.*;

/**
 * RRC 状态转移阶段定义（严格按图：一条主要消息=一个阶段）
 */
public final class RrcStateTransferPhases {

    private RrcStateTransferPhases() {}

    public enum StartType {
        NOT_START,
        PENDING_START,
        CONFIRMED_START
    }

    /**
     * 主线顺序（按图）：
     * 0  Uu: RRCRelease
     * 1  Uu: RRCResumeRequest
     * 2  Xn: Retrieve UE Context Request
     * 3  Xn: Retrieve UE Context Response
     * 4  Uu: RRCResumeComplete（成功结束）
     * 5  Uu: RRCReject（失败结束）
     */
    public static final int PH_UU_RRC_RELEASE = 0;
    public static final int PH_UU_RRC_RESUME_REQ = 1;
    public static final int PH_XN_RETRIEVE_CTX_REQ = 2;
    public static final int PH_XN_RETRIEVE_CTX_RESP = 3;
    public static final int PH_UU_RRC_RESUME_COMPLETE = 4;
    public static final int PH_UU_RRC_REJECT = 5;

    private static final Map<String, PhaseDef.PhaseLocation> LOCATIONS;

    static {
        Map<String, PhaseDef.PhaseLocation> m = new HashMap<>();

        add(m, "RRCRELEASE",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RELEASE, 0, true, true));

        add(m, "RRCRESUMEREQUEST",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RESUME_REQ, 0, true, true));

        add(m, "RETRIEVE UE CONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_XN_RETRIEVE_CTX_REQ, 0, true, true));

        add(m, "RETRIEVE UE CONTEXT RESPONSE",
                new PhaseDef.PhaseLocation(PH_XN_RETRIEVE_CTX_RESP, 0, true, true));

        add(m, "RRCRESUMECOMPLETE",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RESUME_COMPLETE, 0, true, true));

        add(m, "RRCREJECT",
                new PhaseDef.PhaseLocation(PH_UU_RRC_REJECT, 0, true, true));

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
     * - 成功：RRCResumeComplete
     * - 失败：RRCReject
     * - 断开：RRCRelease（不带 suspendConfig 的分支在图里也算“结束”，但 payload 判断在上层做）
     */
    public static boolean isEndMessage(String msgType) {
        if (msgType == null) return false;
        String u = normalize(msgType);

        return u.equals("RRCRESUMECOMPLETE")
                || u.equals("RRCREJECT")
                || u.equals("RRCRELEASE");
    }

    /**
     * 起始判定（推荐）：
     * - RRCResumeRequest：CONFIRMED_START（更强锚点）
     * - RRCRelease：PENDING_START（弱锚点）
     *
     * 你若希望“只要 Release 就建流程”，可在 FlowHandler.shouldCreate 里放开。
     */
    public static StartType checkStartType(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return StartType.NOT_START;
        String u = normalize(msg.getMsgType());

        if (u.equals("RRCRELEASE")) return StartType.PENDING_START;
        if (u.equals("RRCRESUMEREQUEST")) return StartType.CONFIRMED_START;

        return StartType.NOT_START;
    }

    /**
     * payload 校验占位：
     * - RRCRelease：是否含 suspendConfig（决定是否走 resume 链路）
     * - RRCReject：可能需要看 cause（如图里 10b/10c）
     */
    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        return true;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
