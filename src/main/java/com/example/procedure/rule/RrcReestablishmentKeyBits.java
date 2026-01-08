package com.example.procedure.rule;

import java.util.Locale;

/**
 * RRC 重建（RRC Reestablishment）关键消息位图定义
 *
 * 对齐设计原则：
 * 1) 一种关键消息 = 1 bit（幂等）
 * 2) 成功 / 失败收口 mask 分离
 * 3) bitForMsgType 只做映射
 */
public final class RrcReestablishmentKeyBits {

    private RrcReestablishmentKeyBits() {}

    // ===================== 主线关键节点 =====================

    /** Uu: RRCReestablishmentRequest */
    public static final int BIT_RRC_REEST_REQ = 1 << 0;

    /** Xn: Xn-AP Retrieve UE Context Request */
    public static final int BIT_XN_RETRIEVE_CTX_REQ = 1 << 1;

    /** Xn: Xn-AP Retrieve UE Context Response */
    public static final int BIT_XN_RETRIEVE_CTX_RESP = 1 << 2;

    /** Uu: RRCReestablishment */
    public static final int BIT_RRC_REEST = 1 << 3;

    /** Uu: RRCReestablishmentComplete */
    public static final int BIT_RRC_REEST_COMPLETE = 1 << 4;

    // ===================== 成功 / 失败收口 =====================

    /**
     * 成功收口（弱条件，推荐）：
     * - UE 请求重建
     * - 网络侧成功返回 UE Context
     * - UE 完成 RRC 重建
     */
    public static final int REQUIRED_MASK_SUCCESS =
            BIT_RRC_REEST_REQ
                    | BIT_XN_RETRIEVE_CTX_RESP
                    | BIT_RRC_REEST_COMPLETE;

    /**
     * 失败收口（目前图中没有明确失败信令，先占位）
     * 后续可加入：
     * - Retrieve UE Context Failure
     * - 重建拒绝类消息
     */
    public static final int FAILURE_ANY_MASK = 0;

    // ===================== 映射函数 =====================

    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;
        String u = normalize(msgType);

        if (u.equals("RRCREESTABLISHMENTREQUEST")) {
            return BIT_RRC_REEST_REQ;
        }

        if (u.equals("RETRIEVE UE CONTEXT REQUEST")) {
            return BIT_XN_RETRIEVE_CTX_REQ;
        }

        if (u.equals("RETRIEVE UE CONTEXT RESPONSE")) {
            return BIT_XN_RETRIEVE_CTX_RESP;
        }

        if (u.equals("RRCREESTABLISHMENT")) {
            return BIT_RRC_REEST;
        }

        if (u.equals("RRCREESTABLISHMENTCOMPLETE")) {
            return BIT_RRC_REEST_COMPLETE;
        }

        return 0;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
