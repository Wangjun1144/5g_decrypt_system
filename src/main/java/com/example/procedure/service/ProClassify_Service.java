package com.example.procedure.service;

import com.example.procedure.flow.*;
import com.example.procedure.model.*;
import com.example.procedure.rule.ProcedureCloseDecider;
import com.example.procedure.rule.ProcedureRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProClassify_Service {

    private final ProManager_Service proManagerService;
    private final FlowRegistry flowRegistry;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** IA 触发器消息到来时：优先归并到已有 IA 的最低分阈值 */
    private static final int IA_MERGE_THRESHOLD = 35;

    /**
     * 对一条信令做流程识别 & 更新流程上下文
     *
     * 重构后顺序：
     * 1. trigger 优先决策
     * 2. 通用 best-match 决策
     * 3. UNKNOWN 兜底
     *
     * 注意：
     * - 不改变原有功能
     * - 只是把控制流拆清楚
     */
    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        if (msg == null || msg.getUeId() == null) {
            return ProcedureMatchResult.error("msg or ueId is null");
        }

        String ueId = msg.getUeId();
        long nowMs = System.currentTimeMillis();

        List<Procedure> activeList = proManagerService.listActiveProcedures(ueId);
        FlowContext ctx = new FlowContext(proManagerService, new ProcedureCloseDecider());

        // 保持你原有 scorer 逻辑不变
        ScoreScorer scorer = this::scoreProcedure;

        // 1) trigger 优先通道
        FlowMatchDecision triggerDecision = decideByTrigger(msg, activeList, scorer);
        if (triggerDecision != null) {
            return applyDecision(msg, triggerDecision, nowMs, ctx);
        }

        // 2) 通用 best-match
        FlowMatchDecision commonDecision = decideByCommonBestMatch(msg, activeList, scorer);
        if (commonDecision != null) {
            return applyDecision(msg, commonDecision, nowMs, ctx);
        }

        // 3) UNKNOWN 兜底
        return applyDecision(msg, FlowMatchDecision.unknown(), nowMs, ctx);
    }

    /**
     * trigger 优先决策：
     * - 按 FlowRegistry 的顺序遍历 handler
     * - 只有 isTrigger(msg) 为 true 的 handler 才参与
     * - 优先尝试归并
     * - 归并失败后再判断是否允许创建
     *
     * 和你原来的行为保持一致：
     * - 一旦某个 handler 触发过，就不会再继续看后面的 trigger handler
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

            // 与你原有逻辑一致：只要有 handler 触发了，就不再继续看后面的 trigger handler
            break;
        }

        return null;
    }

    /**
     * 通用 best-match：
     * - 当 trigger 通道没有形成决策时使用
     * - 所有 active procedure 都可以竞争
     * - 所有 handler 都会参与
     *
     * 与原逻辑保持一致：
     * - 如果 activeList 为空，不在这里直接建 UNKNOWN，交给外层统一兜底
     * - 只有 bestScore > 0 才接受
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
     * 在所有 handler 中找全局最优匹配
     *
     * 说明：
     * - 你当前每个 handler 都有 chooseBest(...)，它会自己在 activeList 中只挑本类型流程
     * - 这里再从所有 handler 的 best 中选一个全局最高分
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

            // 保持原行为：只有 score > 0 才认为有意义
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

    /**
     * 将匹配决策真正落实到：
     * - 创建流程
     * - 归并并更新流程
     * - UNKNOWN 兜底
     */
    private ProcedureMatchResult applyDecision(
            SignalingMessage msg,
            FlowMatchDecision decision,
            long nowMs,
            FlowContext ctx
    ) {
        if (decision == null) {
            return ProcedureMatchResult.error("flow decision is null");
        }

        switch (decision.getAction()) {
            case ATTACH:
                return attachToExistingProcedure(msg, decision, nowMs, ctx);
            case CREATE:
                return createNewProcedure(msg, decision.getProcedureType());
            case UNKNOWN:
            default:
                return createUnknownProcedure(msg);
        }
    }

    /**
     * 将消息归并到已有流程
     *
     * 注意：
     * 由于当前 FlowMatchDecision 里没有保存 Score，
     * 这里通过 scoreProcedure(...) 重新计算一次 score，再调用 handler.applyUpdate(...)。
     *
     * 这样改动最小，也不会破坏你现有 handler 的 applyUpdate 签名。
     */
    private ProcedureMatchResult attachToExistingProcedure(
            SignalingMessage msg,
            FlowMatchDecision decision,
            long nowMs,
            FlowContext ctx
    ) {
        FlowHandler handler = decision.getHandler();
        Procedure procedure = decision.getProcedure();

        if (handler == null || procedure == null) {
            return ProcedureMatchResult.error("attach decision missing handler or procedure");
        }

        String ueId = msg.getUeId();
        Score score = scoreProcedure(procedure, nowMs, msg);

        if (score == null) {
            return ProcedureMatchResult.error("score is null when attaching procedure");
        }

        handler.applyUpdate(ueId, procedure, score, msg, nowMs, ctx);
        return ProcedureMatchResult.successExisting(procedure.getProcedureId(), handler.type());
    }


    /**
     * 创建新流程
     *
     * 注意：
     * 为了保持你当前行为一致，
     * 这里只调用 add_ActProcedure(...)，不额外调用 handler.applyUpdate(...)。
     */
    private ProcedureMatchResult createNewProcedure(SignalingMessage msg, ProcedureTypeEnum typeEnum) {
        String ueId = msg.getUeId();
        String msgType = msg.getMsgType();

        var created = proManagerService.add_ActProcedure(ueId, typeEnum, msgType);
        if (created == null || (int) created.getOrDefault("status", 1) != 0) {
            return ProcedureMatchResult.error("failed to create " + typeEnum);
        }

        String procId = String.valueOf(created.get("procedureId"));
        return ProcedureMatchResult.successNew(procId, typeEnum);
    }

    /**
     * UNKNOWN 兜底
     *
     * 保持你现有逻辑：
     * - activeList 为空时建 UNKNOWN
     * - 通用 best-match 失败时也建 UNKNOWN
     */
    private ProcedureMatchResult createUnknownProcedure(SignalingMessage msg) {
        String ueId = msg.getUeId();
        String msgType = msg.getMsgType();

        var created = proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.UNKNOWN, msgType);
        if (created == null || (int) created.getOrDefault("status", 1) != 0) {
            return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
        }

        String procId = String.valueOf(created.get("procedureId"));
        return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.UNKNOWN);
    }

    /**
     * 根据流程 type code 反查枚举
     */
    private ProcedureTypeEnum resolveProcedureType(String code) {
        if (code == null) {
            return null;
        }

        for (ProcedureTypeEnum type : ProcedureTypeEnum.values()) {
            if (code.equalsIgnoreCase(type.getCode())) {
                return type;
            }
        }
        return null;
    }


    private Score scoreProcedure(Procedure proc, long msgTs, SignalingMessage msg) {
        ProcedureTypeEnum typeEnum = ProcedureTypeEnum.fromCode(proc.getProcedureTypeCode());

        ProcedureRule rule = null;
        // 让 IA / XHO 都走 rule（你后续会在 ProcedureRule 里补 XHO 的 scoreForProcedure）
        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS || typeEnum == ProcedureTypeEnum.XN_HANDOVER) {
            rule = new ProcedureRule(typeEnum, 60_000L);
        }

        int score = 0;
        Score score1 = new Score(0, -1, -1);

        if (rule != null) {
            Score score2 = rule.scoreForProcedure(proc, msg);
            score1.setScore(score2.getScore());
            score1.setPhaseIndex(score2.getPhaseIndex());
            score1.setOrderIndex(score2.getOrderIndex());
            score += score1.getScore();
        } else {
            if (typeEnum == ProcedureTypeEnum.UNKNOWN) score += 5;
        }

        long lastUpdateMillis = parseTimeMillis(proc.getLastUpdateTime());
        long diff = Math.abs(msgTs - lastUpdateMillis);

        if (diff <= 1_000L) score += 10;
        else if (diff <= 10_000L) score += 5;
        else if (diff <= 60_000L) score += 1;

        if (rule != null && rule.getMaxIdleMillis() > 0 && diff > rule.getMaxIdleMillis()) {
            score -= 20;
        }

        String iface = msg.getIface();
        String layer = msg.getProtocolLayer();

        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS) {
            if ("Uu".equals(iface) && "RRC".equals(layer)) score += 5;
            if ("N2".equals(iface) && "NGAP".equals(layer)) score += 3;
        } else if (typeEnum == ProcedureTypeEnum.XN_HANDOVER) {
            if ("Xn".equals(iface) && "XNAP".equals(layer)) score += 5;
            if ("Uu".equals(iface) && "RRC".equals(layer)) score += 3;
            if ("N2".equals(iface) && "NGAP".equals(layer)) score += 3;
        }

        score1.setScore(score);
        return score1;
    }

    private long parseTimeMillis(String timeStr) {
        if (timeStr == null) return 0L;
        try {
            LocalDateTime dt = LocalDateTime.parse(timeStr, FORMATTER);
            return dt.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
