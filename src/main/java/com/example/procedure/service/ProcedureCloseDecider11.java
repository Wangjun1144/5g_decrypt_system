package com.example.procedure.service;

import com.example.procedure.model.Procedure;
import com.example.procedure.rule.InitialAccessKeyBits;

public final class ProcedureCloseDecider11 {

    private ProcedureCloseDecider11(){}

    /** END_seen 后等待关键消息补齐窗口（完全乱序建议 20~60 秒） */
    public static final long CLOSE_TIMEOUT_MS = 30_000;

    /** 先用 WEAK，后续你想更严格可切到 STRONG */
    public static final int REQUIRED_MASK = InitialAccessKeyBits.REQUIRED_MASK_WEAK;

    public static boolean isReadyToClose(Procedure p, long nowMs) {
        if (p == null || !p.isEndSeen()) return false;

        boolean keysEnough = (p.getKeyMask() & REQUIRED_MASK) == REQUIRED_MASK;
        if (keysEnough) return true;

        long endAt = p.getEndSeenAtMs();
        return endAt > 0 && (nowMs - endAt) > CLOSE_TIMEOUT_MS;
    }
}
