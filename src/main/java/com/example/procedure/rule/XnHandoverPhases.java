package com.example.procedure.rule;

import com.example.procedure.model.SignalingMessage;

import java.util.*;

/**
 * Xn切换（Xn Handover）阶段定义（严格按“解密方工作流程”的主线消息顺序）
 *
 * 设计原则（对齐 InitialAccessPhases）：
 * 1) locate(msgType) -> PhaseLocation(phaseIndex, orderIndex, isPhaseStart)
 * 2) 每一条“主要消息”独占一个 phase（你要求的：一条消息=一个阶段）
 * 3) 允许后续补充非关键消息：只需要在对应 phase 的 msgType 集合里追加即可
 * 4) END/FAIL 类型消息用于触发/收口（后续可扩展）
 */
public final class XnHandoverPhases {

    private XnHandoverPhases() {}

    // ============== StartType（对齐 IA 的 checkStartType） ==============

    public enum StartType {
        NOT_START,
        PENDING_START,
        CONFIRMED_START
    }

    // ============== PhaseIndex：严格按流程图顺序 ==============

    /**
     * 你要求：每一条主要消息代表一个阶段
     *
     * 主线顺序（按图）：
     * 0  Xn: HANDOVER REQUEST
     * 1  Xn: HANDOVER PREPARATION FAILURE / HANDOVER CANCEL（失败分支，可认为“终止阶段”）
     * 2  Uu: HANDOVER COMMAND（第一次）
     * 3  N2: N2 Path Switch Request
     * 4  N2: N2 Path Switch Request Ack
     * 5  Uu: HANDOVER COMMAND（第二次）
     * 6  Uu: RRCReconfigurationComplete
     * 7  Uu: RRCReestablishmentRequest（失败分支）
     * 8  N2: 切换失败相关消息（失败分支，具体 msgType 你后续补）
     */
    public static final int PH_XN_HO_REQUEST = 0;
    public static final int PH_XN_FAIL_OR_CANCEL = 1;
    public static final int PH_UU_HO_COMMAND_1 = 2;
    public static final int PH_N2_PATH_SWITCH_REQ = 3;
    public static final int PH_N2_PATH_SWITCH_ACK = 4;
    public static final int PH_UU_HO_COMMAND_2 = 5;
    public static final int PH_UU_RRC_RECFG_COMPLETE = 6;
    public static final int PH_UU_RRC_REEST_REQ = 7;
    public static final int PH_N2_HO_FAILURE = 8;

    // ============== msgType 映射表：msgType -> PhaseLocation ==============

    /**
     * orderIndex：你说“一条消息=一个阶段”，所以每个 phase 的 order 默认用 0
     * isPhaseStart：我们把主线关键节点都当作可触发 phaseStart（更方便你后续做触发器优先）
     *
     * 对 HANDOVER COMMAND 的“第一次/第二次”：
     * - Phases 层无法只靠 msgType 区分第几次（因为 msgType 一样）
     * - 这里给一个“默认定位到第一次阶段”的 location
     * - 上层（如 ProcedureRule 或 applyXhoUpdate）可基于 proc.keyMask 再判定是否应视为第二次
     */
    private static final Map<String, PhaseDef.PhaseLocation> LOCATIONS;

    static {
        Map<String, PhaseDef.PhaseLocation> m = new HashMap<>();

        // 0) Xn HO Request
        add(m, "HANDOVER REQUEST",
                new PhaseDef.PhaseLocation(PH_XN_HO_REQUEST, 0, true, true));

        // 1) Xn Fail/Cancel（失败/终止）
        add(m, "HANDOVER PREPARATION FAILURE",
                new PhaseDef.PhaseLocation(PH_XN_FAIL_OR_CANCEL, 0, true, true));
        add(m, "HANDOVER CANCEL",
                new PhaseDef.PhaseLocation(PH_XN_FAIL_OR_CANCEL, 0, true, true));

        // 2) Uu HO Command（默认定位为第一次）
        add(m, "HANDOVER COMMAND",
                new PhaseDef.PhaseLocation(PH_UU_HO_COMMAND_1, 0, true, true));
        add(m, "HANDOVERCOMMAND",
                new PhaseDef.PhaseLocation(PH_UU_HO_COMMAND_1, 0, true, true));

        // 3) N2 Path Switch Request
        add(m, "N2 PATH SWITCH REQUEST",
                new PhaseDef.PhaseLocation(PH_N2_PATH_SWITCH_REQ, 0, true, true));

        // 4) N2 Path Switch Ack
        add(m, "N2 PATH SWITCH REQUEST ACK",
                new PhaseDef.PhaseLocation(PH_N2_PATH_SWITCH_ACK, 0, true, true));

        // 6) Uu RRCReconfigurationComplete
        add(m, "RRCRECONFIGURATIONCOMPLETE",
                new PhaseDef.PhaseLocation(PH_UU_RRC_RECFG_COMPLETE, 0, true, true));

        // 7) Uu RRCReestablishmentRequest（失败）
        add(m, "RRCREESTABLISHMENTREQUEST",
                new PhaseDef.PhaseLocation(PH_UU_RRC_REEST_REQ, 0, true, true));

        // 8) N2 切换失败相关消息（你后续补具体 msgType）
        // 这里只给一个占位：如果你的 msgType 里有明确 "FAILURE" 关键字可先粗略匹配
        // 但 locate 是精确映射函数，不建议在这里做 contains 匹配；建议你后续补全具体 msgType。
        // （因此这里暂不 add）

        LOCATIONS = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, PhaseDef.PhaseLocation> m, String msgType, PhaseDef.PhaseLocation loc) {
        m.put(normalize(msgType), loc);
    }

    /**
     * locate：返回 msgType 对应的阶段位置；找不到返回 null
     */
    public static PhaseDef.PhaseLocation locate(String msgType) {
        if (msgType == null) return null;
        return LOCATIONS.get(normalize(msgType));
    }

    // ============== end message / failure message ==============

    /**
     * XnHO 的“结束类”消息（失败/取消/重建/切换失败）：
     * - HANDOVER PREPARATION FAILURE
     * - HANDOVER CANCEL
     * - RRCReestablishmentRequest
     * - N2 切换失败相关消息（你后续补）
     *
     * 成功收口一般发生在 RRCReconfigurationComplete 之后（也可由你上层 CloseDecider 决定）
     */
    public static boolean isEndMessage(String msgType) {
        if (msgType == null) return false;
        String u = normalize(msgType);

        return u.equals("HANDOVER PREPARATION FAILURE")
                || u.equals("HANDOVER CANCEL")
                || u.equals("RRCREESTABLISHMENTREQUEST")
                || u.equals("RRC REESTABLISHMENT REQUEST")
                || u.equals("RRCREESTABLISHMENT REQUEST");
        // N2 failure：等你补具体 msgType，再加到这里
    }

    // ============== checkStartType（对齐 IA 的创建门槛） ==============

    /**
     * XnHO 的“起始判定”：
     * - HANDOVER REQUEST：PENDING_START（准备阶段开始）
     * - 第一次 HANDOVER COMMAND：CONFIRMED_START（更强的开始锚点）
     *
     * 解释：你要求“一条消息=一个阶段”，但在流程创建上最好还是用更强锚点来避免误建流程。
     * 如果你坚持“只要见到 HO REQUEST 就创建”，把 PENDING_START 当 CONFIRMED_START 也行。
     */
    public static StartType checkStartType(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return StartType.NOT_START;

        String u = normalize(msg.getMsgType());

        if (u.equals("HANDOVER REQUEST")) {
            return StartType.PENDING_START;
        }
        if (u.equals("HANDOVER COMMAND") || u.equals("HANDOVERCOMMAND")) {
            return StartType.CONFIRMED_START;
        }
        return StartType.NOT_START;
    }

    // ============== payload 校验占位（先给可用默认） ==============

    /**
     * phaseStart 的 payload 校验：
     * - 先按 IA 的接口留着
     * - 你后续补入非关键消息、以及真正解析字段后，再逐步加强校验
     *
     * 建议校验点（后续你再实现）：
     * - HO REQUEST：是否有 target cell global id / KNG-RAN* / NCC*
     * - HO COMMAND：是否有 NCC / C-RNTI 等
     * - Path Switch Ack：是否有 {NH,NCC} 或 NSCI
     */
    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        // 先默认 true，保证流程可以跑通；后续你再按字段逐步收紧
        return true;
    }

    // ============== 工具：msgType 标准化 ==============

    /**
     * 建议你整个系统都对 msgType 做统一 normalize（IA/XnHO 都适用）
     */
    private static String normalize(String s) {
        if (s == null) return null;
        // trim + 压缩空格 + uppercase
        String t = s.trim().replaceAll("\\s+", " ");
        return t.toUpperCase(Locale.ROOT);
    }
}
