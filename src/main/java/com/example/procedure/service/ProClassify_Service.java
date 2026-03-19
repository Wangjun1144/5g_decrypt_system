package com.example.procedure.service;

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
import com.example.procedure.processing.procedure.ProcedureDecisionExecutor;
import com.example.procedure.processing.procedure.ProcedureScoreService;
import com.example.procedure.rule.ProcedureCloseDecider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 旧的流程识别服务。
 *
 * 当前阶段定位：
 * - 它仍然是旧流程识别主实现
 * - 但本轮进一步聚焦在“识别决策”
 * - 决策落地执行已下沉到 ProcedureDecisionExecutor
 * - 评分逻辑已下沉到 ProcedureScoreService
 *
 * 当前职责：
 * 1. 读取 active procedure 列表
 * 2. 构造 flow context
 * 3. 做 trigger 优先决策
 * 4. 做通用 best-match 决策
 * 5. 将决策交给 ProcedureDecisionExecutor 执行
 *
 * 这一步的意义：
 * - 继续拆薄旧 ProClassify_Service
 * - 让业务职责更贴近你的重构方案
 * - 又不改变当前系统功能
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ProClassify_Service {

    private final ProManager_Service proManagerService;
    private final FlowRegistry flowRegistry;
    private final ProcedureDecisionExecutor procedureDecisionExecutor;
    private final ProcedureScoreService procedureScoreService;

    /**
     * 对一条消息做流程识别并更新流程上下文。
     *
     * 当前顺序：
     * 1. trigger 优先决策
     * 2. 通用 best-match 决策
     * 3. UNKNOWN 兜底
     */
    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        if (msg == null || msg.getUeId() == null) {
            return ProcedureMatchResult.error("msg or ueId is null");
        }

        String ueId = msg.getUeId();
        long nowMs = System.currentTimeMillis();

        List<Procedure> activeList = proManagerService.listActiveProcedures(ueId);
        FlowContext ctx = new FlowContext(proManagerService, new ProcedureCloseDecider());

        // 当前 ScoreScorer 实现已正式下沉到独立评分服务。
        ScoreScorer scorer = procedureScoreService::score;

        // 1) trigger 优先通道
        FlowMatchDecision triggerDecision = decideByTrigger(msg, activeList, scorer);
        if (triggerDecision != null) {
            return procedureDecisionExecutor.apply(msg, triggerDecision, nowMs, ctx, scorer);
        }

        // 2) 通用 best-match
        FlowMatchDecision commonDecision = decideByCommonBestMatch(msg, activeList, scorer);
        if (commonDecision != null) {
            return procedureDecisionExecutor.apply(msg, commonDecision, nowMs, ctx, scorer);
        }

        // 3) UNKNOWN 兜底
        return procedureDecisionExecutor.apply(
                msg,
                FlowMatchDecision.unknown(),
                nowMs,
                ctx,
                scorer
        );
    }

    /**
     * trigger 优先决策：
     * - 按 FlowRegistry 的顺序遍历 handler
     * - 只对 isTrigger(msg) 为 true 的 handler 参与决策
     * - 优先尝试归并
     * - 归并失败后再判断是否允许创建
     *
     * 与现有逻辑保持一致：
     * - 一旦某个 handler 被触发，就不会再继续看后面的 trigger handler
     * - 如果它不允许创建，则交给通用逻辑继续处理
     */
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

            // 优先归并已有流程
            if (best != null
                    && best.getScore() != null
                    && best.getScore().getScore() >= handler.mergeThreshold()) {
                return FlowMatchDecision.attach(handler, best.getProcedure());
            }

            // 归并失败，再判断是否允许创建新流程
            if (handler.shouldCreate(best, msg)) {
                return FlowMatchDecision.create(handler);
            }

            // 保持原有语义：只要某个 trigger handler 触发过，就不再继续看后面的 trigger handler
            break;
        }

        return null;
    }

    /**
     * 通用 best-match：
     * - 当 trigger 通道没有形成决策时使用
     * - 所有 active procedure 都可以竞争
     * - 所有 handler 都参与
     */
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

    /**
     * 在所有 handler 中找全局最优匹配。
     *
     * 当前仍保持原有语义：
     * - 每个 handler 自己先在 activeList 中选本类流程的最佳候选
     * - 再从所有 handler 的最佳候选里选一个全局最高分
     * - 只有 score > 0 才认为有意义
     */
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

            // 保持原有行为：只有 score > 0 才认为有意义
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
