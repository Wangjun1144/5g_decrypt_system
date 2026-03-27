package com.example.procedure.processing.procedure.recognize;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.flow.FlowBestMatch;
import com.example.procedure.processing.procedure.flow.FlowContext;
import com.example.procedure.processing.procedure.flow.FlowHandler;
import com.example.procedure.processing.procedure.flow.FlowHandlerRegistry;
import com.example.procedure.processing.procedure.flow.FlowMatchDecision;
import com.example.procedure.processing.procedure.flow.ProcedureClosePolicy;
import com.example.procedure.processing.procedure.flow.ProcedureScoreResult;
import com.example.procedure.processing.procedure.flow.ScoreScorer;
import com.example.procedure.processing.procedure.score.ProcedureScoringService;
import com.example.procedure.processing.procedure.stage.ProcedureDecisionExecutor;
import com.example.procedure.processing.procedure.state.ProcedureStateService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程识别入口服务。
 *
 * 当前职责：
 * 1. 加载某个 UE 的当前活跃流程集合。
 * 2. 先执行 trigger 优先决策，再执行通用 best-match 决策。
 * 3. 在无法识别时回落到 UNKNOWN 流程。
 * 4. 将最终识别决策交给阶段执行器落地。
 *
 * 设计说明：
 * - 这里是流程子域的正式识别入口。
 * - 内部组合 flow 运行时、score 服务、state 服务和 stage 执行器。
 * - 未来如果要把识别器独立出去，这个类就是最自然的应用层边界。
 */
@Service
public class ProcedureRecognitionService {
    // REFACTOR STEP: PROCEDURE_STAGE_SUBPACKAGE_REORG
    // REFACTOR STEP: PROCEDURE_RECOGNIZE_SCORE_SUBPACKAGE_REORG

    private final ProcedureStateService procedureStateService;
    // REFACTOR STEP: FLOW_RUNTIME_REORG
    private final FlowHandlerRegistry flowRegistry;
    private final ProcedureDecisionExecutor procedureDecisionExecutor;
    private final ProcedureScoringService procedureScoringService;
    // REFACTOR STEP: RULE_FLOW_BOUNDARY
    private final ProcedureClosePolicy procedureClosePolicy;

    public ProcedureRecognitionService(
            ProcedureStateService procedureStateService,
            FlowHandlerRegistry flowRegistry,
            ProcedureDecisionExecutor procedureDecisionExecutor,
            ProcedureScoringService procedureScoringService,
            ProcedureClosePolicy procedureClosePolicy
    ) {
        this.procedureStateService = procedureStateService;
        this.flowRegistry = flowRegistry;
        this.procedureDecisionExecutor = procedureDecisionExecutor;
        this.procedureScoringService = procedureScoringService;
        this.procedureClosePolicy = procedureClosePolicy;
    }

    public ProcedureMatchResult recognize(SignalingMessage msg) {
        return recognizeDetailed(msg).getMatchResult();
    }

    /**
     * Runs the full recognition flow and returns the typed recognition outcome.
     */
    public ProcedureRecognitionOutcome recognizeDetailed(SignalingMessage msg) {
        if (msg == null || msg.getUeId() == null) {
            return ProcedureRecognitionOutcome.of(
                    ProcedureMatchResult.error("msg or ueId is null"),
                    false,
                    false,
                    true
            );
        }

        String ueId = msg.getUeId();
        long nowMs = System.currentTimeMillis();

        List<Procedure> activeList = procedureStateService.listActiveProcedures(ueId);
        FlowContext ctx = new FlowContext(procedureStateService, procedureClosePolicy);

        ScoreScorer scorer = procedureScoringService::score;

        FlowMatchDecision triggerDecision = decideByTrigger(msg, activeList, scorer);
        if (triggerDecision != null) {
            return ProcedureRecognitionOutcome.of(
                    procedureDecisionExecutor.apply(msg, triggerDecision, nowMs, ctx, scorer),
                    true,
                    false,
                    false
            );
        }

        FlowMatchDecision commonDecision = decideByCommonBestMatch(msg, activeList, scorer);
        if (commonDecision != null) {
            return ProcedureRecognitionOutcome.of(
                    procedureDecisionExecutor.apply(msg, commonDecision, nowMs, ctx, scorer),
                    false,
                    true,
                    false
            );
        }

        return ProcedureRecognitionOutcome.of(
                procedureDecisionExecutor.apply(
                        msg,
                        FlowMatchDecision.unknown(),
                        nowMs,
                        ctx,
                        scorer
                ),
                false,
                false,
                true
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
