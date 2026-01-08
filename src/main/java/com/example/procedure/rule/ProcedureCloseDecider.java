package com.example.procedure.rule;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.rule.InitialAccessKeyBits;
import com.example.procedure.rule.XnHandoverKeyBits;

public class ProcedureCloseDecider {

    /** IA：成功收口所需的关键位（你已有的那套） */
    private static final int IA_REQUIRED_MASK =
            InitialAccessKeyBits.REQUIRED_MASK_WEAK; // 或你自己的命名

    /** XnHO：成功收口（弱条件） */
    private static final int XHO_REQUIRED_MASK =
            XnHandoverKeyBits.REQUIRED_MASK_SUCCESS_WEAK;

    /**
     * 是否可以关闭该流程
     */
    public boolean isReadyToClose(Procedure proc, long nowMs) {

        ProcedureTypeEnum type =
                ProcedureTypeEnum.fromCode(proc.getProcedureTypeCode());

        int keyMask = proc.getKeyMask();

        switch (type) {

            case INITIAL_ACCESS:
                return isIaReadyToClose(proc, nowMs);

            case XN_HANDOVER:
                return isXhoReadyToClose(proc, nowMs);

            default:
                return false;
        }
    }

    // ================= IA =================

    private boolean isIaReadyToClose(Procedure proc, long nowMs) {

        // 1) 成功收口：END 已见 + 关键消息齐全
        if (proc.isEndSeen()
                && (proc.getKeyMask() & IA_REQUIRED_MASK) == IA_REQUIRED_MASK) {
            return true;
        }

        // 2) 兜底超时（防止流程永远挂着）
        // 你原来就有 maxIdleMillis 的概念，这里可以沿用
        // 示例：END 已见 + 超过 5 秒
        if (proc.isEndSeen() && nowMs - proc.getEndSeenAtMs() > 5_000L) {
            return true;
        }

        return false;
    }

    // ================= XnHO =================

    private boolean isXhoReadyToClose(Procedure proc, long nowMs) {

        int keyMask = proc.getKeyMask();

        // 1) 失败收口：任意失败类 bit 出现即可
        if ((keyMask & XnHandoverKeyBits.FAILURE_ANY_MASK) != 0) {
            return true;
        }

        // 2) 成功收口：关键成功位齐全
        if ((keyMask & XHO_REQUIRED_MASK) == XHO_REQUIRED_MASK) {
            return true;
        }

        // 3) 可选兜底超时（防止异常悬挂）
        // 比如 30 秒没动静
//        if (nowMs - proc.getLastUpdateMillis() > 30_000L) {
//            return true;
//        }

        return false;
    }
}
