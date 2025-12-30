package com.example.procedure.rule;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;
import lombok.Getter;

import java.util.*;

@Getter
public class ProcedureRule {

    private final ProcedureTypeEnum type;

    /** 最大允许间隔（毫秒），超过认为这个流程已经“过时” */
    private final long maxIdleMillis;

    public ProcedureRule(ProcedureTypeEnum type,
                         long maxIdleMillis) {
        this.type = type;
        this.maxIdleMillis = maxIdleMillis;
    }

    /**
     * 为当前流程计算这条信令的“归属得分”
     *
     *  - 对 INITIAL_ACCESS 使用：lastPhaseIndex + lastOrderIndex + InitialAccessPhases
     *  - 对其他流程，先用 canFollow 简单返回 100 / -100
     *
     * 评分规则：
     *  1) 如果当前信令是同一阶段的“下一条” => 高分（100）
     *  2) 否则，如果是“下一阶段的起始关键信令” => 较高分（80）
     *  3) 否则 => 负分（-100）
     */
    public Score scoreForProcedure(Procedure proc, SignalingMessage msg) {
        String msgType = msg.getMsgType();
        Score score = new Score(-100, -1, -1);

        PhaseDef.PhaseLocation loc = InitialAccessPhases.locate(msgType);

        if (loc == null) {
            // END 可能不在 phases.messages[] 里（比如 Response/Complete）
            if (InitialAccessPhases.isEndMessage(msgType)) {
                score.setScore(30);
                score.setPhaseIndex(proc.getLastPhaseIndex());
                score.setOrderIndex(proc.getLastOrderIndex());
                return score;
            }
            score.setScore(-100);
            return score;
        }

        int s = 30;                 // 属于 IA
        if (loc.isKey()) s += 20;   // 关键消息优先
        if (loc.isPhaseStart()) s += 5;

        // 允许乱序：只做轻量偏好，不强依赖
        int lastPhase = proc.getLastPhaseIndex();
        int lastOrder = proc.getLastOrderIndex();
        int msgPhase = loc.getPhaseIndex();
        int msgOrder = loc.getOrderIndex();

        if (lastPhase < 0) {
            s += loc.isPhaseStart() ? 20 : 5;
        } else {
            int d = msgPhase - lastPhase;
            if (d == 0) s += (msgOrder >= lastOrder) ? 15 : 5;
            else if (d == 1) s += 20;
            else if (d > 1) s += 10;
            else s -= 5 * Math.min(3, -d);
        }

        if (InitialAccessPhases.isEndMessage(msgType)) s += 20;

        score.setScore(s);
        score.setPhaseIndex(msgPhase);
        score.setOrderIndex(msgOrder);
        return score;
    }


    /** 下标是否合法 */
    private boolean isValidPhaseIndex(int index, List<PhaseDef> phases) {
        return index >= 0 && index < phases.size();
    }

    /** 是否处于“最后一个阶段的最后一条消息” */
    private boolean isLastPhaseLastMessage(int phaseIndex,
                                           int orderIndex,
                                           List<PhaseDef> phases) {
        if (!isValidPhaseIndex(phaseIndex, phases)) {
            return false;
        }
        int lastPhaseIdx = phases.size() - 1;
        if (phaseIndex != lastPhaseIdx) {
            return false;
        }
        PhaseDef lastPhase = phases.get(lastPhaseIdx);
        String[] msgs = lastPhase.getMessages();
        return orderIndex == msgs.length - 1;
    }

}
