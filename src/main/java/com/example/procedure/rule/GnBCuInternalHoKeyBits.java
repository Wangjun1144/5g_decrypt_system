package com.example.procedure.rule;

import java.util.Locale;

/**
 * gNB-CU 内部切换（CU Internal Handover）关键消息位图定义
 *
 * 流程图关键点：
 * 1) Uu: HandoverCommand / RRCReconfiguration（包含 MasterKeyUpdate、算法配置、PCI 等信息）
 * 2) Uu: RRCReconfigurationComplete（成功）
 * 3) Uu: RRCReestablishmentRequest（失败）
 *
 * 设计原则对齐 IA/Xn/N2：
 * - 一种关键消息=1bit（幂等）
 * - 成功/失败收口 mask
 * - bitForMsgType 只做映射
 */
public final class GnBCuInternalHoKeyBits {

    private GnBCuInternalHoKeyBits() {}

    // ===================== 主线关键节点 =====================

    /** Uu: HandoverCommand/RRCReconfiguration（图中合并入口） */
    public static final int BIT_UU_HO_CMD_OR_RRC_RECFG = 1 << 0;

    /** Uu: RRCReconfigurationComplete（成功收口） */
    public static final int BIT_UU_RRC_RECFG_COMPLETE = 1 << 1;

    // ===================== 失败/异常节点 =====================

    /** Uu: RRCReestablishmentRequest（失败收口） */
    public static final int BIT_UU_RRC_REEST_REQ = 1 << 2;

    // ===================== REQUIRED MASK（收口条件） =====================

    /**
     * 成功收口（弱/强都一样即可）：
     * - 至少看到：入口消息 + RRCReconfigurationComplete
     */
    public static final int REQUIRED_MASK_SUCCESS =
            BIT_UU_HO_CMD_OR_RRC_RECFG | BIT_UU_RRC_RECFG_COMPLETE;

    /**
     * 失败收口：命中任一失败类消息
     */
    public static final int FAILURE_ANY_MASK =
            BIT_UU_RRC_REEST_REQ;

    // ===================== 映射函数 =====================

    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;
        String u = normalize(msgType);

        // 你要求只写“第一个标准 msgType”
        if (u.equals("HANDOVERCOMMAND/RRCRECONFIGURATION")) {
            return BIT_UU_HO_CMD_OR_RRC_RECFG;
        }

        if (u.equals("RRCRECONFIGURATIONCOMPLETE")) {
            return BIT_UU_RRC_RECFG_COMPLETE;
        }

        if (u.equals("RRCREESTABLISHMENTREQUEST")) {
            return BIT_UU_RRC_REEST_REQ;
        }

        return 0;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
