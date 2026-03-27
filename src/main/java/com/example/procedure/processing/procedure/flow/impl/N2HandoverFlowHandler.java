package com.example.procedure.processing.procedure.flow.impl;

import com.example.procedure.processing.procedure.flow.FlowHandler;
import com.example.procedure.model.*;
import com.example.procedure.processing.procedure.flow.FlowContext;
import com.example.procedure.processing.procedure.flow.ProcedureScoreResult;
import com.example.procedure.processing.procedure.flow.ScoreScorer;
import com.example.procedure.processing.procedure.ruledef.N2HandoverKeyBits;
import com.example.procedure.processing.procedure.ruledef.N2HandoverPhases;
import com.example.procedure.processing.procedure.support.ProcedureProgressUtil;

import java.util.List;
import java.util.Locale;

public class N2HandoverFlowHandler implements FlowHandler {

    private static final int N2HO_MERGE_THRESHOLD = 35;

    @Override
    public ProcedureTypeEnum type() {
        // 纭繚浣犵殑鏋氫妇閲屾湁璇ョ被鍨嬶紱濡傛灉浣犱滑鍛藉悕涓嶅悓锛堜緥濡?N2_SWITCH / N2_HO锛夛紝鎸夐」鐩疄闄呮敼
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
        // 鎺ㄨ崘鍙厑璁?CONFIRMED_START锛圢2 HANDOVER REQUEST锛夋潵寤烘祦绋?
        return N2HandoverPhases.checkStartType(msg) == N2HandoverPhases.StartType.CONFIRMED_START;
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis(); // 鑻?msg 鑷甫鏃堕棿鎴筹紝寤鸿鏀圭敤 msg.getXXXTimestamp()
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

    // 濡傛灉浣犲悗缁鍦?handler 閲屽仛鐗规畩鍒嗘祦锛堢被浼?Xn 鐨?HO COMMAND 1/2锛夛紝鍙湪杩欓噷鍔?isXXX(msgType) 宸ュ叿鍑芥暟
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
