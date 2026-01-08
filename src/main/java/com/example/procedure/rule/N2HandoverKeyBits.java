package com.example.procedure.rule;

import java.util.Locale;

/**
 * N2 切换（N2 Handover）关键消息位图定义
 *
 * 设计原则（对齐 XnHandoverKeyBits / IA）：
 * 1) 同一种关键消息只占 1 个 bit（幂等）
 * 2) 支持“成功收口”和“失败收口”两类 mask
 * 3) bitForMsgType 只做映射，不做业务逻辑
 *
 * msgType 字符串需与解析器输出一致；建议上层先 normalize（trim/upper/压缩空格）
 */
public final class N2HandoverKeyBits {

    private N2HandoverKeyBits() {}

    // ===================== 主线关键节点（按第一张图） =====================

    /** N14: Namf_Communication_Create UEContext Request（拿 {NH=KgNB,NCC=0} / KAMF 等） */
    public static final int BIT_N14_UECONTEXT_REQ = 1 << 0;

    /** N2: NGAP HANDOVER REQUEST */
    public static final int BIT_N2_HO_REQUEST = 1 << 1;

    /** Uu: RRCReconfiguration（基于 NCC/KgNB 派生 AS 子密钥、确定算法、DRB 激活等） */
    public static final int BIT_UU_RRC_RECONFIG = 1 << 2;

    /** N2: NGAP HANDOVER NOTIFY（从 User Location Info 获取目标小区 NCGI 等） */
    public static final int BIT_N2_HO_NOTIFY = 1 << 3;

    // ===================== 失败/异常节点（占位，可按你后续补全） =====================

    /** N2: 切换失败相关消息（例如 HandoverFailure / PathSwitchFailure 等，后续补具体映射） */
    public static final int BIT_N2_HO_FAILURE = 1 << 4;

    // ===================== REQUIRED MASK（收口条件） =====================

    /**
     * 成功收口（弱条件，推荐先用）：
     * - 至少看到：N2 HO REQUEST + Uu RRCReconfiguration + N2 HO NOTIFY
     *
     * 解释：
     * - HO REQUEST：切换开始（接收侧拿到必要上下文）
     * - RRCReconfiguration：派生/激活 AS 安全，准备生效
     * - HO NOTIFY：确认切换完成后的位置更新信息到达
     */
    public static final int REQUIRED_MASK_SUCCESS_WEAK =
            BIT_N2_HO_REQUEST | BIT_UU_RRC_RECONFIG | BIT_N2_HO_NOTIFY;

    /**
     * 成功收口（强条件，更严格但更易因缺失无法满足）：
     * - 再要求：N14 UEContext Request（有更完整的初始安全上下文链路）
     */
    public static final int REQUIRED_MASK_SUCCESS_STRONG =
            REQUIRED_MASK_SUCCESS_WEAK | BIT_N14_UECONTEXT_REQ;

    /**
     * 失败收口命中（出现任意一种即可认为流程失败结束）
     */
    public static final int FAILURE_ANY_MASK =
            BIT_N2_HO_FAILURE;

    // ===================== 映射函数 =====================

    /**
     * 将解析后的 msgType 映射为 bit。
     * 建议上层先 normalize msgType（trim + upper + 合并空格）后再传入。
     */
    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;

        String u = normalize(msgType);

        // 主线
        if (u.equals("NAMF_COMMUNICATION_CREATE UECONTEXT REQUEST")
                || u.equals("NAMF COMMUNICATION CREATE UECONTEXT REQUEST")
                || u.equals("UECONTEXT REQUEST")
                || u.equals("UE CONTEXT REQUEST")) {
            return BIT_N14_UECONTEXT_REQ;
        }

        if (u.equals("HANDOVER REQUEST")) return BIT_N2_HO_REQUEST;

        if (u.equals("RRCRECONFIGURATION") || u.equals("RRC RECONFIGURATION")) {
            return BIT_UU_RRC_RECONFIG;
        }

        if (u.equals("HANDOVER NOTIFY")) return BIT_N2_HO_NOTIFY;

        // 失败（先给一个兜底占位；建议后续用精确 msgType 补全）
        if (u.contains("HANDOVER") && u.contains("FAIL")) return BIT_N2_HO_FAILURE;
        if (u.contains("N2") && u.contains("FAIL")) return BIT_N2_HO_FAILURE;

        return 0;
    }

    private static String normalize(String s) {
        // trim + 压缩空格 + uppercase
        String t = s.trim().replaceAll("\\s+", " ");
        return t.toUpperCase(Locale.ROOT);
    }
}
