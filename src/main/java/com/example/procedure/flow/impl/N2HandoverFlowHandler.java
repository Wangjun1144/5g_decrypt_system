package com.example.procedure.flow.impl;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.N2HandoverKeyBits;
import com.example.procedure.rule.N2HandoverPhases;
import com.example.procedure.util.ProcedureProgressUtil;

import java.util.List;
import java.util.Locale;

public class N2HandoverFlowHandler implements FlowHandler {

    private static final int N2HO_MERGE_THRESHOLD = 35;

    @Override
    public ProcedureTypeEnum type() {
        // 确保你的枚举里有该类型；如果你们命名不同（例如 N2_SWITCH / N2_HO），按项目实际改
        return ProcedureTypeEnum.N2_HANDOVER;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return N2HO_MERGE_THRESHOLD;
    }

    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        if (N2HandoverPhases.isEndMessage(t)) return true;

        var loc = N2HandoverPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return N2HandoverPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return N2HandoverKeyBits.bitForMsgType(t) != 0;
    }

    @Override
    public boolean shouldCreate(ProcedureScoreResult proceScoreResult, SignalingMessage msg) {
        // 推荐只允许 CONFIRMED_START（N2 HANDOVER REQUEST）来建流程
        return N2HandoverPhases.checkStartType(msg) == N2HandoverPhases.StartType.CONFIRMED_START;
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis(); // 若 msg 自带时间戳，建议改用 msg.getXXXTimestamp()
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode()) != ProcedureTypeEnum.N2_HANDOVER) continue;
            Score s = scorer.score(p, msgTs, msg);
            if (s != null && s.getScore() > best) {
                best = s.getScore();
                bestProc = p;
                bestScore = s;
            }
        }
        return bestProc == null ? null : new ProcedureScoreResult(bestProc, bestScore);
    }

    @Override
    public void applyUpdate(String ueId, Procedure proc, Score score, SignalingMessage msg, long nowMs, FlowContext ctx) {
        String msgType = msg.getMsgType();

        if (N2HandoverPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        int bit = N2HandoverKeyBits.bitForMsgType(msgType);
        if (bit != 0) proc.setKeyMask(proc.getKeyMask() | bit);

        ProcedureProgressUtil.advanceMonotonic(proc, score.getPhaseIndex(), score.getOrderIndex());

        ctx.proManagerService().update_ActProcedureEx(
                ueId,
                proc.getProcedureId(),
                msgType,
                proc.getLastPhaseIndex(),
                proc.getLastOrderIndex(),
                proc.isEndSeen(),
                proc.getEndSeenAtMs(),
                proc.getKeyMask()
        );

        if (ctx.closeDecider().isReadyToClose(proc, nowMs)) {
            ctx.proManagerService().end_Procedure(ueId, proc.getProcedureId());
        }
    }

    // 如果你后续要在 handler 里做特殊分流（类似 Xn 的 HO COMMAND 1/2），可在这里加 isXXX(msgType) 工具函数
    @SuppressWarnings("unused")
    private boolean isRrcReconfiguration(String msgType) {
        if (msgType == null) return false;
        String u = normalize(msgType);
        return u.equals("RRCRECONFIGURATION") || u.equals("RRC RECONFIGURATION");
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
