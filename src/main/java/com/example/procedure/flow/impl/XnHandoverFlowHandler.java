package com.example.procedure.flow.impl;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.XnHandoverKeyBits;
import com.example.procedure.rule.XnHandoverPhases;
import com.example.procedure.util.ProcedureProgressUtil;

import java.util.List;
import java.util.Locale;

public class XnHandoverFlowHandler implements FlowHandler {

    private static final int XHO_MERGE_THRESHOLD = 35;

    @Override
    public ProcedureTypeEnum type() {
        return ProcedureTypeEnum.XN_HANDOVER;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return XHO_MERGE_THRESHOLD;
    }

    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        if (XnHandoverPhases.isEndMessage(t)) return true;

        var loc = XnHandoverPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return XnHandoverPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return XnHandoverKeyBits.bitForMsgType(t) != 0;
    }

    @Override
    public boolean shouldCreate(ProcedureScoreResult proceScoreResult, SignalingMessage msg) {
        // 推荐只允许 CONFIRMED_START（例如第一次 HO COMMAND）
        return XnHandoverPhases.checkStartType(msg) == XnHandoverPhases.StartType.CONFIRMED_START;
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis();
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode()) != ProcedureTypeEnum.XN_HANDOVER) continue;
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

        if (XnHandoverPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        int bit;
        if (isHandoverCommand(msgType)) {
            bit = XnHandoverKeyBits.bitForHandoverCommandWithContext(proc.getKeyMask());
        } else {
            bit = XnHandoverKeyBits.bitForMsgType(msgType);
        }
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

    private boolean isHandoverCommand(String msgType) {
        if (msgType == null) return false;
        String u = msgType.trim().toUpperCase(Locale.ROOT);
        return u.equals("HANDOVER COMMAND") || u.equals("HANDOVERCOMMAND");
    }
}
