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

        if (msgType == null) {
            score.setScore(-100);
            return score;
        }

        // 1) 根据流程类型选择 locate/isEnd
        PhaseDef.PhaseLocation loc;
        boolean isEnd;

        if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
            loc = InitialAccessPhases.locate(msgType);
            isEnd = InitialAccessPhases.isEndMessage(msgType);
        } else if (type == ProcedureTypeEnum.XN_HANDOVER) {
            loc = XnHandoverPhases.locate(msgType);
            isEnd = XnHandoverPhases.isEndMessage(msgType);
        } else {
            // 其他流程暂不打规则分
            score.setScore(-100);
            return score;
        }

        // 2) END 可能不在 phases 里：给一个“可归并但不推进”的基础分
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

        // 3) 计算 base 分
        int s = 30; // 属于该流程的基础分

        // IA 用 loc.isKey；XHO 你 phases 里未设置 isKey（可能默认 false），因此对 XHO 我们改用 keyBits 加权
        if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
            if (loc.isKey()) s += 20;
            if (loc.isPhaseStart()) s += 5;
        } else if (type == ProcedureTypeEnum.XN_HANDOVER) {
            // XHO：每条主要消息都可视为 phaseStart，所以 phaseStart 加分仍然有效
            if (loc.isPhaseStart()) s += 8;

            // XHO：关键消息用 KeyBits 来判定
            int bit = XnHandoverKeyBits.bitForMsgType(msgType);
            if (isHandoverCommand(msgType)) {
                // HO COMMAND 两次：这里用当前 keyMask 决定本次算第几次（用于加权/后续 phase 修正）
                bit = XnHandoverKeyBits.bitForHandoverCommandWithContext(proc.getKeyMask());
            }
            if (bit != 0) s += 20;
        }

        // 4) 乱序/顺序偏好（IA 轻量；XHO 更严格）
        int lastPhase = proc.getLastPhaseIndex();
        int lastOrder = proc.getLastOrderIndex();
        int msgPhase = loc.getPhaseIndex();
        int msgOrder = loc.getOrderIndex();

        // XHO 特殊：HANDOVER COMMAND 的第二次出现要“修正 phase”
        if (type == ProcedureTypeEnum.XN_HANDOVER && isHandoverCommand(msgType)) {
            // 如果已经见过第一次 HO_COMMAND，则本次应落到 “第二次 HO_COMMAND 的 phase”
            // 你的 XnHandoverPhases 里第二次 HO_COMMAND 是 PH_UU_HO_COMMAND_2
            if ((proc.getKeyMask() & XnHandoverKeyBits.BIT_UU_HO_COMMAND_1) != 0) {
                msgPhase = XnHandoverPhases.PH_UU_HO_COMMAND_2;
                msgOrder = 0;
            } else {
                msgPhase = XnHandoverPhases.PH_UU_HO_COMMAND_1;
                msgOrder = 0;
            }
        }

        if (lastPhase < 0) {
            // 还没开始：phaseStart 强加分
            s += loc.isPhaseStart() ? 20 : 5;
        } else {
            int d = msgPhase - lastPhase;

            if (type == ProcedureTypeEnum.INITIAL_ACCESS) {
                // IA：轻量偏好（你原来的逻辑）
                if (d == 0) s += (msgOrder >= lastOrder) ? 15 : 5;
                else if (d == 1) s += 20;
                else if (d > 1) s += 10;
                else s -= 5 * Math.min(3, -d);
            } else {
                // XHO：更严格的顺序偏好（符合你“严格按图顺序”的需求）
                if (d == 0) {
                    // 同阶段（你的设计一般 order=0），给中等分；如果 order 回退则小惩罚
                    s += (msgOrder >= lastOrder) ? 12 : 2;
                } else if (d == 1) {
                    // 下一阶段：最符合严格顺序
                    s += 25;
                } else if (d > 1) {
                    // 跳跃：可能中间丢包，仍可归并但降低信心
                    s += 8;
                } else {
                    // 回退：XHO 不鼓励回退（比 IA 更狠）
                    s -= 15 * Math.min(2, -d);
                }
            }
        }

        if (isEnd) s += 20;

        score.setScore(s);
        score.setPhaseIndex(msgPhase);
        score.setOrderIndex(msgOrder);
        return score;
    }

    private boolean isHandoverCommand(String msgType) {
        if (msgType == null) return false;
        String u = msgType.trim().toUpperCase(Locale.ROOT);
        return u.equals("HANDOVER COMMAND") || u.equals("HANDOVERCOMMAND");
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
