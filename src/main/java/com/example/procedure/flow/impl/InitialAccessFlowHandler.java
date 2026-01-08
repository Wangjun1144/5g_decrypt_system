package com.example.procedure.flow.impl;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.InitialAccessKeyBits;
import com.example.procedure.rule.InitialAccessPhases;
import com.example.procedure.rule.PhaseDef;
import com.example.procedure.util.ProcedureProgressUtil;

import java.util.List;

public class InitialAccessFlowHandler implements FlowHandler {

    private static final int IA_MERGE_THRESHOLD = 35;

    @Override
    public ProcedureTypeEnum type() {
        return ProcedureTypeEnum.INITIAL_ACCESS;
    }

    @Override
    public boolean hasRule() {
        return true;
    }

    @Override
    public int mergeThreshold() {
        return IA_MERGE_THRESHOLD;
    }

    /** IA 触发器：end / phaseStart(valid) / keyBit */
    @Override
    public boolean isTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        if (InitialAccessPhases.isEndMessage(t)) return true;

        PhaseDef.PhaseLocation loc = InitialAccessPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return InitialAccessPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        return InitialAccessKeyBits.bitForMsgType(t) != 0;
    }

    /**
     * 建议：触发器可以宽，但创建要严（避免“碎 IA”）
     * 你若坚持“关键消息也能创建”，这里返回 true 即可
     */
    @Override
    public boolean shouldCreate(ProcedureScoreResult procScoreResult,SignalingMessage msg) {
        // 推荐：仅 CONFIRMED_START 允许创建
        if(InitialAccessPhases.checkStartType(msg) != InitialAccessPhases.StartType.NOT_START){
            return true;
        }else{
            if(procScoreResult == null) return true;
            return hasSeenKeyMessage(procScoreResult.getProcedure(), msg);
        }
    }

    @Override
    public ProcedureScoreResult chooseBest(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis();
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode()) != ProcedureTypeEnum.INITIAL_ACCESS) continue;
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

        // 1) END 只标记，不立刻结束
        if (InitialAccessPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        // 2) keyMask
        int bit = InitialAccessKeyBits.bitForMsgType(msgType);
        if (bit != 0) proc.setKeyMask(proc.getKeyMask() | bit);

        // 3) 单调推进
        ProcedureProgressUtil.advanceMonotonic(proc, score.getPhaseIndex(), score.getOrderIndex());

        // 4) 持久化
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

        // 5) close
        if (ctx.closeDecider().isReadyToClose(proc, nowMs)) {
            ctx.proManagerService().end_Procedure(ueId, proc.getProcedureId());
        }
    }

    private boolean hasSeenKeyMessage(Procedure proc, SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return false;
        int bit = InitialAccessKeyBits.bitForMsgType(msg.getMsgType());
        if (bit == 0) return false;
        return (proc.getKeyMask() & bit) != 0;
    }

}
