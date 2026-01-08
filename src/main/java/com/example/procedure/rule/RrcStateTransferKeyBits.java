package com.example.procedure.rule;

import java.util.Locale;

/**
 * RRC 状态转移（Release/Resume + Xn Retrieve UE Context）关键消息位图定义
 *
 * 对齐设计原则：
 * 1) 一种关键消息 = 1bit（幂等）
 * 2) 成功/失败收口 mask
 * 3) bitForMsgType 只做映射，不做业务逻辑
 */
public final class RrcStateTransferKeyBits {

    private RrcStateTransferKeyBits() {}

    // ===================== 主线关键节点 =====================

    /** Uu: RRCRelease（可能带 suspendConfig，也可能不带；bit 层不区分） */
    public static final int BIT_UU_RRC_RELEASE = 1 << 0;

    /** Uu: RRCResumeRequest */
    public static final int BIT_UU_RRC_RESUME_REQ = 1 << 1;

    /** Xn: Retrieve UE Context Request */
    public static final int BIT_XN_RETRIEVE_CTX_REQ = 1 << 2;

    /** Xn: Retrieve UE Context Response */
    public static final int BIT_XN_RETRIEVE_CTX_RESP = 1 << 3;

    /** Uu: RRCResumeComplete（成功） */
    public static final int BIT_UU_RRC_RESUME_COMPLETE = 1 << 4;

    // ===================== 失败/异常节点 =====================

    /** Uu: RRCReject（重连失败分支） */
    public static final int BIT_UU_RRC_REJECT = 1 << 5;

    // ===================== REQUIRED MASK（收口条件） =====================

    /**
     * 成功收口（弱条件，推荐）：
     * - ResumeRequest + RetrieveCtxResp + ResumeComplete
     */
    public static final int REQUIRED_MASK_SUCCESS_WEAK =
            BIT_UU_RRC_RESUME_REQ | BIT_XN_RETRIEVE_CTX_RESP | BIT_UU_RRC_RESUME_COMPLETE;

    /**
     * 成功收口（强条件）：
     * - 再要求：RRCRelease + RetrieveCtxReq
     */
    public static final int REQUIRED_MASK_SUCCESS_STRONG =
            REQUIRED_MASK_SUCCESS_WEAK | BIT_UU_RRC_RELEASE | BIT_XN_RETRIEVE_CTX_REQ;

    /**
     * 失败收口：命中任一失败类消息
     */
    public static final int FAILURE_ANY_MASK =
            BIT_UU_RRC_REJECT;

    // ===================== 映射函数 =====================

    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;
        String u = normalize(msgType);

        // 只写“第一个标准 msgType”
        if (u.equals("RRCRELEASE")) return BIT_UU_RRC_RELEASE;
        if (u.equals("RRCRESUMEREQUEST")) return BIT_UU_RRC_RESUME_REQ;

        if (u.equals("RETRIEVE UE CONTEXT REQUEST")) return BIT_XN_RETRIEVE_CTX_REQ;
        if (u.equals("RETRIEVE UE CONTEXT RESPONSE")) return BIT_XN_RETRIEVE_CTX_RESP;

        if (u.equals("RRCRESUMECOMPLETE")) return BIT_UU_RRC_RESUME_COMPLETE;

        if (u.equals("RRCREJECT")) return BIT_UU_RRC_REJECT;

        return 0;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
