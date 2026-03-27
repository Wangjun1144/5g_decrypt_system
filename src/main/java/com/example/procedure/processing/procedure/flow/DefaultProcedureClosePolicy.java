package com.example.procedure.processing.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.processing.procedure.ruledef.InitialAccessKeyBits;
import com.example.procedure.processing.procedure.ruledef.XnHandoverKeyBits;
import org.springframework.stereotype.Component;

/**
 * 默认流程关闭策略实现。
 *
 * 当前定位：
 * 1. 这是流程运行期的正式关闭策略实现
 * 2. 负责根据 keyMask、endSeen、超时等运行态信息判断是否可关闭
 * 3. 与 rule 包中的静态 phases / key bits 定义分离
 */
@Component
public class DefaultProcedureClosePolicy implements ProcedureClosePolicy {

    /**
     * IA 成功收口所需的关键位。
     */
    private static final int IA_REQUIRED_MASK =
            InitialAccessKeyBits.REQUIRED_MASK_WEAK;

    /**
     * XnHO 成功收口所需的关键位。
     */
    private static final int XHO_REQUIRED_MASK =
            XnHandoverKeyBits.REQUIRED_MASK_SUCCESS_WEAK;

    /**
     * 判断当前流程是否可以关闭。
     *
     * @param procedure 当前流程
     * @param nowMs 当前时间戳
     * @return true 表示可以关闭
     */
    @Override
    // REFACTOR STEP: RULE_FLOW_BOUNDARY
    public boolean isReadyToClose(Procedure procedure, long nowMs) {
        ProcedureTypeEnum type =
                ProcedureTypeEnum.fromCode(procedure.getProcedureTypeCode());

        return switch (type) {
            case INITIAL_ACCESS -> isIaReadyToClose(procedure, nowMs);
            case XN_HANDOVER -> isXhoReadyToClose(procedure);
            default -> false;
        };
    }

    private boolean isIaReadyToClose(Procedure procedure, long nowMs) {
        if (procedure.isEndSeen()
                && (procedure.getKeyMask() & IA_REQUIRED_MASK) == IA_REQUIRED_MASK) {
            return true;
        }

        return procedure.isEndSeen()
                && nowMs - procedure.getEndSeenAtMs() > 5_000L;
    }

    private boolean isXhoReadyToClose(Procedure procedure) {
        int keyMask = procedure.getKeyMask();

        if ((keyMask & XnHandoverKeyBits.FAILURE_ANY_MASK) != 0) {
            return true;
        }

        return (keyMask & XHO_REQUIRED_MASK) == XHO_REQUIRED_MASK;
    }
}
