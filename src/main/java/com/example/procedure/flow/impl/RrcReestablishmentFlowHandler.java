package com.example.procedure.flow.impl;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.RrcReestablishmentKeyBits;
import com.example.procedure.rule.RrcReestablishmentPhases;
import com.example.procedure.util.ProcedureProgressUtil;

import java.util.List;

public class RrcReestablishmentFlowHandler implements FlowHandler {

    private static final int REEST_MERGE_THRESHOLD = 20;

    @Override
    public ProcedureTypeEnum type() {
        return ProcedureTypeEnum.RRC_REESTABLISH;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return REEST_MERGE_THRESHOLD;
    }

    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        if (RrcReestablishmentPhases.isEndMessage(t)) return true;

        var loc = RrcReestablishmentPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return RrcReestablishmentPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return RrcReestablishmentKeyBits.bitForMsgType(t) != 0;
    }

    @Override
    public boolean shouldCreate(ProcedureScoreResult scoreResult, SignalingMessage msg) {
        return RrcReestablishmentPhases.checkStartType(msg)
                == RrcReestablishmentPhases.StartType.CONFIRMED_START;
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList,
                                           SignalingMessage msg,
                                           ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis();
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode())
                    != ProcedureTypeEnum.RRC_REESTABLISH) {
                continue;
            }
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
    public void applyUpdate(String ueId,
                            Procedure proc,
                            Score score,
                            SignalingMessage msg,
                            long nowMs,
                            FlowContext ctx) {

        String msgType = msg.getMsgType();

        if (RrcReestablishmentPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) {
                proc.setEndSeenAtMs(nowMs);
            }
        }

        int bit = RrcReestablishmentKeyBits.bitForMsgType(msgType);
        if (bit != 0) {
            proc.setKeyMask(proc.getKeyMask() | bit);
        }

        ProcedureProgressUtil.advanceMonotonic(
                proc,
                score.getPhaseIndex(),
                score.getOrderIndex()
        );

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
}
