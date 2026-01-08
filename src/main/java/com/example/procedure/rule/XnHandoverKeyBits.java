package com.example.procedure.rule;

import java.util.Locale;

/**
 * Xn 切换（Xn Handover）关键消息位图定义
 *
 * 设计原则（与 IA 一致）：
 * 1) 同一种关键消息只占 1 个 bit（幂等）
 * 2) 支持“成功收口”和“失败收口”两类 REQUIRED_MASK
 * 3) bitForMsgType 只做映射，不做业务逻辑
 *
 * 注意：
 * - msgType 的字符串需要与你解析器输出一致；建议在上层先做 normalize（trim/upper/去多空格）
 */
public final class XnHandoverKeyBits {

    private XnHandoverKeyBits() {}

    // ===================== 主线关键节点 =====================

    /** Xn: HANDOVER REQUEST（获取 {KNG-RAN*, NCC*} + Target Cell Global ID 等） */
    public static final int BIT_XN_HO_REQUEST = 1 << 0;

    /** Uu: HANDOVER COMMAND（第一次出现：拿 NCC/CellId/C-RNTI/ID 映射/准备切换密钥） */
    public static final int BIT_UU_HO_COMMAND_1 = 1 << 1;

    /** N2: N2 Path Switch Request（更新/确认 NGAP ID 映射） */
    public static final int BIT_N2_PATH_SWITCH_REQ = 1 << 2;

    /** N2: N2 Path Switch Request Ack（拿 {NH,NCC}/NSCI 分支关键） */
    public static final int BIT_N2_PATH_SWITCH_ACK = 1 << 3;

    /** Uu: HANDOVER COMMAND（第二次出现：用最新 NH/KgNB 再派生一次） */
    public static final int BIT_UU_HO_COMMAND_2 = 1 << 4;

    /** Uu: RRCReconfigurationComplete（确认 gNB-CU 内部切换成功/密钥生效点） */
    public static final int BIT_UU_RRC_RECFG_COMPLETE = 1 << 5;

    // ===================== 失败/异常节点 =====================

    /** Xn: HANDOVER PREPARATION FAILURE（准备失败） */
    public static final int BIT_XN_PREP_FAILURE = 1 << 6;

    /** Xn: HANDOVER CANCEL（取消） */
    public static final int BIT_XN_HO_CANCEL = 1 << 7;

    /** Uu: RRCReestablishmentRequest（切换失败时的重建请求） */
    public static final int BIT_UU_RRC_REEST_REQ = 1 << 8;

    /** N2: 切换失败相关消息（图里未给定具体名称，先用泛化 bit） */
    public static final int BIT_N2_HO_FAILURE = 1 << 9;

    // ===================== REQUIRED MASK（收口条件） =====================

    /**
     * 成功收口（弱条件，推荐先用）：
     * - 至少看到：第一次 HO COMMAND + Path Switch Ack + RRCReconfigurationComplete
     *
     * 解释：
     * - HO COMMAND：拿到 NCC/准备切换
     * - Path Switch Ack：NSCI / {NH,NCC} 分支关键
     * - RRCReconfigurationComplete：成功生效点
     */
    public static final int REQUIRED_MASK_SUCCESS_WEAK =
            BIT_UU_HO_COMMAND_1 | BIT_N2_PATH_SWITCH_ACK | BIT_UU_RRC_RECFG_COMPLETE;

    /**
     * 成功收口（强条件，更严格但更容易因丢包无法满足）：
     * - 再要求：HO REQUEST + Path Switch Req + 第二次 HO COMMAND
     */
    public static final int REQUIRED_MASK_SUCCESS_STRONG =
            REQUIRED_MASK_SUCCESS_WEAK | BIT_XN_HO_REQUEST | BIT_N2_PATH_SWITCH_REQ | BIT_UU_HO_COMMAND_2;

    /**
     * 失败收口条件（出现任意一种即可认为流程失败结束）
     * 注意：这个 mask 不是“必须全部出现”，而是用于判断是否命中失败类事件。
     */
    public static final int FAILURE_ANY_MASK =
            BIT_XN_PREP_FAILURE | BIT_XN_HO_CANCEL | BIT_UU_RRC_REEST_REQ | BIT_N2_HO_FAILURE;

    // ===================== 映射函数 =====================

    /**
     * 将解析后的 msgType 映射为 bit。
     * 建议上层先 normalize msgType（trim + upper + 合并空格）后再传入。
     */
    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;
        String t = msgType.trim();

        // 你项目里 msgType 可能大小写/空格不一致，尽量兼容
        String u = t.toUpperCase(Locale.ROOT);

        // 主线
        if (u.equals("HANDOVER REQUEST")) return BIT_XN_HO_REQUEST;
        if (u.equals("N2 PATH SWITCH REQUEST")) return BIT_N2_PATH_SWITCH_REQ;
        if (u.equals("N2 PATH SWITCH REQUEST ACK") || u.equals("N2 PATH SWITCH REQUEST ACKNOWLEDGE")
                || u.equals("N2 PATH SWITCH REQUEST ACKNOWLEDGE")) return BIT_N2_PATH_SWITCH_ACK;
        if (u.equals("RRCRECONFIGURATIONCOMPLETE") || u.equals("RRC RECONFIGURATION COMPLETE")
                || u.equals("RRCRECONFIGURATION COMPLETE")) return BIT_UU_RRC_RECFG_COMPLETE;

        // HANDOVER COMMAND 在图中出现两次：KeyBits 层不区分第几次
        // 更合理的做法是：在 applyXhoUpdate 里根据“是否已见过 HO_COMMAND_1”来决定置 BIT_1 还是 BIT_2
        if (u.equals("HANDOVER COMMAND") || u.equals("HANDOVERCOMMAND")) {
            // 这里只返回一个“HO_COMMAND 标记”，由上层决定落到 _1 还是 _2
            return BIT_UU_HO_COMMAND_1;
        }

        // 失败/取消
        if (u.equals("HANDOVER PREPARATION FAILURE")) return BIT_XN_PREP_FAILURE;
        if (u.equals("HANDOVER CANCEL")) return BIT_XN_HO_CANCEL;

        if (u.equals("RRCREESTABLISHMENTREQUEST") || u.equals("RRC REESTABLISHMENT REQUEST")
                || u.equals("RRCREESTABLISHMENT REQUEST")) return BIT_UU_RRC_REEST_REQ;

        // N2 failure：图里没写具体 msgType，建议你后续补具体映射（例如 PathSwitchFailure / HandoverFailure 等）
        if (u.contains("FAIL") && u.contains("N2")) return BIT_N2_HO_FAILURE;

        return 0;
    }

    /**
     * 上层在遇到 HANDOVER COMMAND 时，可用此方法把它区分为第 1 次或第 2 次出现。
     * 逻辑：如果流程已经见过 HO_COMMAND_1，则本次视为 HO_COMMAND_2。
     */
    public static int bitForHandoverCommandWithContext(int currentKeyMask) {
        if ((currentKeyMask & BIT_UU_HO_COMMAND_1) == 0) return BIT_UU_HO_COMMAND_1;
        return BIT_UU_HO_COMMAND_2;
    }
}
