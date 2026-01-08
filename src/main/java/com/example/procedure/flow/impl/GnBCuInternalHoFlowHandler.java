package com.example.procedure.flow.impl;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.GnBCuInternalHoKeyBits;
import com.example.procedure.rule.GnBCuInternalHoPhases;
import com.example.procedure.util.ProcedureProgressUtil;

import java.util.List;

public class GnBCuInternalHoFlowHandler implements FlowHandler {

    private static final int CU_INTRA_MERGE_THRESHOLD = 20;

    @Override
    public ProcedureTypeEnum type() {
        // 确保你的枚举存在该类型；如命名不同按项目实际改
        return ProcedureTypeEnum.GNBCUINTERNAL_HANDOVER;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return CU_INTRA_MERGE_THRESHOLD;
    }

    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        if (GnBCuInternalHoPhases.isEndMessage(t)) return true;

        var loc = GnBCuInternalHoPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return GnBCuInternalHoPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return GnBCuInternalHoKeyBits.bitForMsgType(t) != 0;
    }

    @Override
    public boolean shouldCreate(ProcedureScoreResult scoreResult, SignalingMessage msg) {
        return GnBCuInternalHoPhases.checkStartType(msg)
                == GnBCuInternalHoPhases.StartType.CONFIRMED_START;
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis();
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode())
                    != ProcedureTypeEnum.GNBCUINTERNAL_HANDOVER) {
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
    public void applyUpdate(String ueId, Procedure proc, Score score, SignalingMessage msg, long nowMs, FlowContext ctx) {
        String msgType = msg.getMsgType();

        if (GnBCuInternalHoPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        int bit = GnBCuInternalHoKeyBits.bitForMsgType(msgType);
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
}
