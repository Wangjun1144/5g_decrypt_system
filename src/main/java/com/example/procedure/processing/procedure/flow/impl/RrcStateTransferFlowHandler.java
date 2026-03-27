package com.example.procedure.processing.procedure.flow.impl;

import com.example.procedure.processing.procedure.flow.FlowHandler;
import com.example.procedure.model.*;
import com.example.procedure.processing.procedure.flow.FlowContext;
import com.example.procedure.processing.procedure.flow.ProcedureScoreResult;
import com.example.procedure.processing.procedure.flow.ScoreScorer;
import com.example.procedure.processing.procedure.ruledef.RrcStateTransferKeyBits;
import com.example.procedure.processing.procedure.ruledef.RrcStateTransferPhases;
import com.example.procedure.processing.procedure.support.ProcedureProgressUtil;

import java.util.List;

public class RrcStateTransferFlowHandler implements FlowHandler {

    private static final int RRC_STATE_MERGE_THRESHOLD = 25;

    @Override
    public ProcedureTypeEnum type() {
        // 纭繚鏋氫妇瀛樺湪璇ョ被鍨嬶紱濡傚懡鍚嶄笉鍚屾寜椤圭洰瀹為檯鏀?
        return ProcedureTypeEnum.RRCSTATE_TRANSFER;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return RRC_STATE_MERGE_THRESHOLD;
    }

    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        // 娉ㄦ剰锛氳繖閲屾妸 RRCRelease 涔熷綋浣?end message锛屼細瑙﹀彂
        if (RrcStateTransferPhases.isEndMessage(t)) return true;

        var loc = RrcStateTransferPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return RrcStateTransferPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return RrcStateTransferKeyBits.bitForMsgType(t) != 0;
    }

    @Override
    public boolean shouldCreate(ProcedureScoreResult scoreResult, SignalingMessage msg) {
        // 鎺ㄨ崘锛氬彧鍏佽 CONFIRMED_START锛圧RCResumeRequest锛夊垱寤?
        return RrcStateTransferPhases.checkStartType(msg)
                == RrcStateTransferPhases.StartType.CONFIRMED_START;
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
                    != ProcedureTypeEnum.RRCSTATE_TRANSFER) {
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

        if (RrcStateTransferPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        int bit = RrcStateTransferKeyBits.bitForMsgType(msgType);
        if (bit != 0) proc.setKeyMask(proc.getKeyMask() | bit);

        ProcedureProgressUtil.advanceMonotonic(proc, score.getPhaseIndex(), score.getOrderIndex());

        ctx.procedureStateService().updateProcedureEx(
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
            ctx.procedureStateService().endProcedure(ueId, proc.getProcedureId());

        }
    }
}
