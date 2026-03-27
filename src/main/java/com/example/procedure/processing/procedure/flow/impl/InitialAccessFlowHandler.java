package com.example.procedure.processing.procedure.flow.impl;

import com.example.procedure.processing.procedure.flow.FlowHandler;
import com.example.procedure.model.*;
import com.example.procedure.processing.procedure.flow.FlowContext;
import com.example.procedure.processing.procedure.flow.ProcedureScoreResult;
import com.example.procedure.processing.procedure.flow.ScoreScorer;
import com.example.procedure.processing.procedure.ruledef.InitialAccessKeyBits;
import com.example.procedure.processing.procedure.ruledef.InitialAccessPhases;
import com.example.procedure.processing.procedure.ruledef.PhaseDef;
import com.example.procedure.processing.procedure.support.ProcedureProgressUtil;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
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

    /** IA 瑙﹀彂鍣細end / phaseStart(valid) / keyBit */
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
     * 寤鸿锛氳Е鍙戝櫒鍙互瀹斤紝浣嗗垱寤鸿涓ワ紙閬垮厤鈥滅 IA鈥濓級
     * 浣犺嫢鍧氭寔鈥滃叧閿秷鎭篃鑳藉垱寤衡€濓紝杩欓噷杩斿洖 true 鍗冲彲
     */
    @Override
    public boolean shouldCreate(ProcedureScoreResult procScoreResult,SignalingMessage msg) {
        // 鎺ㄨ崘锛氫粎 CONFIRMED_START 鍏佽鍒涘缓
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

        // 1) END 鍙爣璁帮紝涓嶇珛鍒荤粨鏉?
        if (InitialAccessPhases.isEndMessage(msgType)) {
            proc.setEndSeen(true);
            if (proc.getEndSeenAtMs() == 0L) proc.setEndSeenAtMs(nowMs);
        }

        // 2) keyMask
        int bit = InitialAccessKeyBits.bitForMsgType(msgType);
        if (bit != 0) proc.setKeyMask(proc.getKeyMask() | bit);

        // 3) 鍗曡皟鎺ㄨ繘
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


        // 5) close
        if (ctx.closeDecider().isReadyToClose(proc, nowMs)) {
            ctx.procedureStateService().endProcedure(ueId, proc.getProcedureId());
        }
    }

    private boolean hasSeenKeyMessage(Procedure proc, SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) return false;
        int bit = InitialAccessKeyBits.bitForMsgType(msg.getMsgType());
        if (bit == 0) return false;
        return (proc.getKeyMask() & bit) != 0;
    }

}
