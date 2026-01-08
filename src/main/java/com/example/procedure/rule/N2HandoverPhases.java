package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;

import java.util.*;

/**
 * N2切换（N2 Handover）阶段定义（按第一张图“解密方工作流程”的主线消息顺序）
 *
 * 设计原则（对齐 IA / XnHandoverPhases）：
 * 1) locate(msgType) -> PhaseLocation(phaseIndex, orderIndex, isPhaseStart)
 * 2) 一条主要消息 = 一个阶段
 * 3) 允许后续补充非关键消息：在对应 phase 的 msgType 集合里追加即可
 */
public final class N2HandoverPhases {

    private N2HandoverPhases() {}

    // ============== StartType（对齐 IA 的 checkStartType） ==============

    public enum StartType {
        NOT_START,
        PENDING_START,
        CONFIRMED_START
    }

    // ============== PhaseIndex：严格按第一张图顺序 ==============

    /**
     * 主线顺序（第一张图）：
     * 0  N14: Namf_Communication_Create UEContext Request
     * 1  N2 : HANDOVER REQUEST
     * 2  Uu : RRCReconfiguration
     * 3  N2 : HANDOVER NOTIFY
     * 4  N2 : 切换失败相关消息（占位）
     */
    public static final int PH_N14_UECONTEXT_REQ = 0;
    public static final int PH_N2_HO_REQUEST = 1;
    public static final int PH_UU_RRC_RECONFIG = 2;
    public static final int PH_N2_HO_NOTIFY = 3;
    public static final int PH_N2_HO_FAILURE = 4;

    // ============== msgType -> PhaseLocation ==============

    private static final Map<String, PhaseDef.PhaseLocation> LOCATIONS;

    static {
        Map<String, PhaseDef.PhaseLocation> m = new HashMap<>();

        // 0) N14 UEContext Request
        add(m, "NAMF_COMMUNICATION_CREATE UECONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_N14_UECONTEXT_REQ, 0, true, true));
        add(m, "NAMF COMMUNICATION CREATE UECONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_N14_UECONTEXT_REQ, 0, true, true));
        add(m, "UECONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_N14_UECONTEXT_REQ, 0, true, true));
        add(m, "UE CONTEXT REQUEST",
                new PhaseDef.PhaseLocation(PH_N14_UECONTEXT_REQ, 0, true, true));

        // 1) N2 HANDOVER REQUEST
        add(m, "HANDOVER REQUEST",
                new PhaseDef.PhaseLocation(PH_N2_HO_REQUEST, 0, true, true));

        // 2) Uu RRCReconfiguration
        add(m, "RRCRECONFIGURATION",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RECONFIG, 0, true, true));
        add(m, "RRC RECONFIGURATION",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RECONFIG, 0, true, true));

        // 3) N2 HANDOVER NOTIFY
        add(m, "HANDOVER NOTIFY",
                new PhaseDef.PhaseLocation(PH_N2_HO_NOTIFY, 0, true, true));

        // 失败消息（建议后续补具体 msgType；此处不做 contains 匹配）
        // add(m, "HANDOVER FAILURE", new PhaseLocation(PH_N2_HO_FAILURE, 0, true, true));

        LOCATIONS = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, PhaseDef.PhaseLocation> m, String msgType, PhaseDef.PhaseLocation loc) {
        m.put(normalize(msgType), loc);
    }

    public static PhaseDef.PhaseLocation locate(String msgType) {
        if (msgType == null) return null;
        return LOCATIONS.get(normalize(msgType));
    }

    // ============== end message / failure message ==============

    /**
     * N2HO 的“结束类”消息：
     * - 成功：HANDOVER NOTIFY（一般可视为成功收口的结束信号之一）
     * - 失败：后续补具体 msgType
     */
    public static boolean isEndMessage(String msgType) {
        if (msgType == null) return false;
        String u = normalize(msgType);

        if (u.equals("HANDOVER NOTIFY")) return true;

        // 失败占位：等你补具体 msgType 后再加精确判断
        // if (u.equals("HANDOVER FAILURE")) return true;

        return false;
    }

    // ============== checkStartType（对齐 IA 的创建门槛） ==============

    /**
     * 起始判定：
     * - N14 UEContext Request：PENDING_START（弱开始锚点）
     * - N2 HANDOVER REQUEST：CONFIRMED_START（推荐更强锚点）
     *
     * 如果你希望“只要看到 N14 就创建”，可以把 PENDING 当 CONFIRMED 用在 shouldCreate。
     */
    public static StartType checkStartType(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return StartType.NOT_START;

        String u = normalize(msg.getMsgType());

        if (u.equals("NAMF_COMMUNICATION_CREATE UECONTEXT REQUEST")
                || u.equals("NAMF COMMUNICATION CREATE UECONTEXT REQUEST")
                || u.equals("UECONTEXT REQUEST")
                || u.equals("UE CONTEXT REQUEST")) {
            return StartType.PENDING_START;
        }

        if (u.equals("HANDOVER REQUEST")) {
            return StartType.CONFIRMED_START;
        }

        return StartType.NOT_START;
    }

    // ============== payload 校验占位（先给可用默认） ==============

    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        // 先默认 true，保证流程跑通；后续可按图里字段逐步收紧：
        // - UEContext Request：{NH=KgNB,NCC=0} / KAMF / ngKSI / NAS COUNT 等
        // - HO Request：security context / 算法标识等
        // - HO Notify：User Location Info / NCGI 等
        return true;
    }

    private static String normalize(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.toUpperCase(Locale.ROOT);
    }
}
