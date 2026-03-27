package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.ruledef.InitialAccessPhases;
import com.example.procedure.processing.procedure.ruledef.PhaseDef;
import com.example.procedure.processing.procedure.ruledef.XnHandoverKeyBits;
import com.example.procedure.processing.procedure.ruledef.XnHandoverPhases;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * 流程评分规则对象。
 *
 * 当前定位：
 * 1. 这是流程运行时评分子域中的规则对象
 * 2. 它虽然依赖静态 phase/key bits 定义，但服务于运行期评分和归并判断
 * 3. 不再放在 rule 包中，避免把静态规则定义与运行期规则对象混在一起
 */
@Getter
public class ProcedureRule {

    private final ProcedureTypeEnum type;
    private final long maxIdleMillis;

    public ProcedureRule(ProcedureTypeEnum type, long maxIdleMillis) {
        this.type = type;
        this.maxIdleMillis = maxIdleMillis;
    }

    /**
     * 为当前流程计算这条消息的归属评分。
     *
     * @param proc 当前流程
     * @param msg 当前消息
     * @return 评分结果
     */
    // REFACTOR STEP: PROCEDURE_RULE_REHOME
    public Score scoreForProcedure(Procedure proc, SignalingMessage msg) {
        String msgType = msg.getMsgType();
        Score score = new Score(-100, -1, -1);

        if (msgType == null) {
            score.setScore(-100);
            return score;
        }

        PhaseDef.PhaseLocation loc;
        boolean isEnd;

        if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
            loc = InitialAccessPhases.locate(msgType);
            isEnd = InitialAccessPhases.isEndMessage(msgType);
        } else if (type == ProcedureTypeEnum.XN_HANDOVER) {
            loc = XnHandoverPhases.locate(msgType);
            isEnd = XnHandoverPhases.isEndMessage(msgType);
        } else {
            score.setScore(-100);
            return score;
        }

        if (loc == null) {
            if (isEnd) {
                score.setScore(30);
                score.setPhaseIndex(proc.getLastPhaseIndex());
                score.setOrderIndex(proc.getLastOrderIndex());
                return score;
            }
            score.setScore(-100);
            return score;
        }

        int s = 30;

        if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
            if (loc.isKey()) {
                s += 20;
            }
            if (loc.isPhaseStart()) {
                s += 5;
            }
        } else if (type == ProcedureTypeEnum.XN_HANDOVER) {
            if (loc.isPhaseStart()) {
                s += 8;
            }

            int bit = XnHandoverKeyBits.bitForMsgType(msgType);
            if (isHandoverCommand(msgType)) {
                bit = XnHandoverKeyBits.bitForHandoverCommandWithContext(proc.getKeyMask());
            }
            if (bit != 0) {
                s += 20;
            }
        }

        int lastPhase = proc.getLastPhaseIndex();
        int lastOrder = proc.getLastOrderIndex();
        int msgPhase = loc.getPhaseIndex();
        int msgOrder = loc.getOrderIndex();

        if (type == ProcedureTypeEnum.XN_HANDOVER && isHandoverCommand(msgType)) {
            if ((proc.getKeyMask() & XnHandoverKeyBits.BIT_UU_HO_COMMAND_1) != 0) {
                msgPhase = XnHandoverPhases.PH_UU_HO_COMMAND_2;
                msgOrder = 0;
            } else {
                msgPhase = XnHandoverPhases.PH_UU_HO_COMMAND_1;
                msgOrder = 0;
            }
        }

        if (lastPhase < 0) {
            s += loc.isPhaseStart() ? 20 : 5;
        } else {
            int d = msgPhase - lastPhase;

            if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
                if (d == 0) {
                    s += (msgOrder >= lastOrder) ? 15 : 5;
                } else if (d == 1) {
                    s += 20;
                } else if (d > 1) {
                    s += 10;
                } else {
                    s -= 5 * Math.min(3, -d);
                }
            } else {
                if (d == 0) {
                    s += (msgOrder >= lastOrder) ? 12 : 2;
                } else if (d == 1) {
                    s += 25;
                } else if (d > 1) {
                    s += 8;
                } else {
                    s -= 15 * Math.min(2, -d);
                }
            }
        }

        if (isEnd) {
            s += 20;
        }

        score.setScore(s);
        score.setPhaseIndex(msgPhase);
        score.setOrderIndex(msgOrder);
        return score;
    }

    private boolean isHandoverCommand(String msgType) {
        if (msgType == null) {
            return false;
        }
        String u = msgType.trim().toUpperCase(Locale.ROOT);
        return u.equals("HANDOVER COMMAND") || u.equals("HANDOVERCOMMAND");
    }

    private boolean isValidPhaseIndex(int index, List<PhaseDef> phases) {
        return index >= 0 && index < phases.size();
    }

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
