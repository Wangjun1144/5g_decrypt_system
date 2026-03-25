package com.example.procedure.processing.procedure;

import com.example.procedure.flow.FlowBestMatch;
import com.example.procedure.flow.FlowContext;
import com.example.procedure.flow.FlowHandler;
import com.example.procedure.flow.FlowMatchDecision;
import com.example.procedure.flow.FlowRegistry;
import com.example.procedure.flow.ProcedureScoreResult;
import com.example.procedure.flow.ScoreScorer;
import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.flow.ProcedureClosePolicy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程识别服务。
 *
 * 当前阶段定位：
 * - 这是“流程处理阶段”中的正式识别组件
 * - 负责承接旧 ProClassify_Service 的核心识别逻辑
 * - 不改变当前已有识别规则、评分语义和返回结果结构
 *
 * 当前职责：
 * 1. 加载某个 UE 当前活跃流程
 * 2. 执行 trigger 优先决策
 * 3. 执行通用 best-match 决策
 * 4. 在无法识别时走 UNKNOWN 兜底
 * 5. 将最终决策交给 ProcedureDecisionExecutor 落地
 *
 * 这样做的意义：
 * - 新主链不再依赖旧 ProClassify_Service
 * - processing.procedure 成为正式的流程识别边界
 * - 后续继续拆分流程领域服务时，可以围绕这里演进
 */
@Service
public class ProcedureRecognitionService {

    private final ProcedureStateService procedureStateService;
    private final FlowRegistry flowRegistry;
    private final ProcedureDecisionExecutor procedureDecisionExecutor;
    private final ProcedureScoreService procedureScoreService;
    // REFACTOR STEP: RULE_FLOW_BOUNDARY
    private final ProcedureClosePolicy procedureClosePolicy;

    public ProcedureRecognitionService(
            ProcedureStateService procedureStateService,
            FlowRegistry flowRegistry,
            ProcedureDecisionExecutor procedureDecisionExecutor,
            ProcedureScoreService procedureScoreService,
            ProcedureClosePolicy procedureClosePolicy
    ) {
        this.procedureStateService = procedureStateService;
        this.flowRegistry = flowRegistry;
        this.procedureDecisionExecutor = procedureDecisionExecutor;
        this.procedureScoreService = procedureScoreService;
        this.procedureClosePolicy = procedureClosePolicy;
    }

    public ProcedureMatchResult recognize(SignalingMessage msg) {
        if (msg == null || msg.getUeId() == null) {
            return ProcedureMatchResult.error("msg or ueId is null");
        }

        String ueId = msg.getUeId();
        long nowMs = System.currentTimeMillis();

        List<Procedure> activeList = procedureStateService.listActiveProcedures(ueId);
        FlowContext ctx = new FlowContext(procedureStateService, procedureClosePolicy);

        ScoreScorer scorer = procedureScoreService::score;

        FlowMatchDecision triggerDecision = decideByTrigger(msg, activeList, scorer);
        if (triggerDecision != null) {
            return procedureDecisionExecutor.apply(msg, triggerDecision, nowMs, ctx, scorer);
        }

        FlowMatchDecision commonDecision = decideByCommonBestMatch(msg, activeList, scorer);
        if (commonDecision != null) {
            return procedureDecisionExecutor.apply(msg, commonDecision, nowMs, ctx, scorer);
        }

        return procedureDecisionExecutor.apply(
                msg,
                FlowMatchDecision.unknown(),
                nowMs,
                ctx,
                scorer
        );
    }

    private FlowMatchDecision decideByTrigger(
            SignalingMessage msg,
            List<Procedure> activeList,
            ScoreScorer scorer
    ) {
        for (FlowHandler handler : flowRegistry.handlers()) {
            if (!handler.isTrigger(msg)) {
                continue;
            }

            ProcedureScoreResult best = handler.chooseBest(activeList, msg, scorer);

            if (best != null
                    && best.getScore() != null
                    && best.getScore().getScore() >= handler.mergeThreshold()) {
                return FlowMatchDecision.attach(handler, best.getProcedure());
            }

            if (handler.shouldCreate(best, msg)) {
                return FlowMatchDecision.create(handler);
            }

            break;
        }

        return null;
    }

    private FlowMatchDecision decideByCommonBestMatch(
            SignalingMessage msg,
            List<Procedure> activeList,
            ScoreScorer scorer
    ) {
        if (activeList == null || activeList.isEmpty()) {
            return null;
        }

        FlowBestMatch best = findBestAcrossAllHandlers(activeList, msg, scorer);
        if (best == null) {
            return null;
        }

        return FlowMatchDecision.attach(best.getHandler(), best.getProcedure());
    }

    private FlowBestMatch findBestAcrossAllHandlers(
            List<Procedure> activeList,
            SignalingMessage msg,
            ScoreScorer scorer
    ) {
        FlowBestMatch bestMatch = null;
        int bestScoreValue = Integer.MIN_VALUE;

        for (FlowHandler handler : flowRegistry.handlers()) {
            ProcedureScoreResult best = handler.chooseBest(activeList, msg, scorer);
            if (best == null || best.getProcedure() == null || best.getScore() == null) {
                continue;
            }

            int scoreValue = best.getScore().getScore();
            if (scoreValue <= 0) {
                continue;
            }

            if (scoreValue > bestScoreValue) {
                bestScoreValue = scoreValue;
                bestMatch = new FlowBestMatch(handler, best.getProcedure(), best.getScore());
            }
        }

        return bestMatch;
    }
}
