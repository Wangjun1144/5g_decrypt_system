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
public class ProClassify_Service_c {

    private final ProManager_Service proManagerService;
    private final FlowRegistry flowRegistry;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** IA 触发器消息到来时：优先归并到已有 IA 的最低分阈值 */
    private static final int IA_MERGE_THRESHOLD = 35;

    /**
     * 对一条信令做流程识别 & 更新流程上下文
     */
    public ProcedureMatchResult handleMessage(SignalingMessage msg) {
        if (msg == null || msg.getUeId() == null) {
            return ProcedureMatchResult.error("msg or ueId is null");
        }

        String ueId = msg.getUeId();
        String msgType = msg.getMsgType();
        long nowMs = System.currentTimeMillis();

        List<Procedure> activeList = proManagerService.listActiveProcedures(ueId);
        FlowContext ctx = new FlowContext(proManagerService, new ProcedureCloseDecider());

        // 把你现有 scoreProcedure 作为 scorer 注入
        ScoreScorer scorer = this::scoreProcedure;

        // ===== A) 触发器优先通道：按 registry 顺序匹配（IA 优先于 XHO）=====
        for (FlowHandler h : flowRegistry.handlers()) {
            if (!h.isTrigger(msg)) continue;

            ProcedureScoreResult best = h.chooseBest(activeList, msg, scorer);

            if (best != null && best.getScore() != null && best.getScore().getScore() >= h.mergeThreshold()) {
                h.applyUpdate(ueId, best.getProcedure(), best.getScore(), msg, nowMs, ctx);
                return ProcedureMatchResult.successExisting(best.getProcedure().getProcedureId(), h.type());
            }

            if (h.shouldCreate(best, msg)) {
                var created = proManagerService.add_ActProcedure(ueId, h.type(), msgType);
                if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                    return ProcedureMatchResult.error("failed to create " + h.type());
                }
                String procId = String.valueOf(created.get("procedureId"));
                return ProcedureMatchResult.successNew(procId, h.type());
            }

            // 触发了但不允许新建：继续走通用逻辑
            break;
        }

        // ===== B) 通用逻辑：如果没有任何 active 流程，建 UNKNOWN =====
        if (activeList == null || activeList.isEmpty()) {
            var created = proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.UNKNOWN, msgType);
            if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
            }
            String procId = String.valueOf(created.get("procedureId"));
            return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.UNKNOWN);
        }

        // ===== C) 通用 best：所有 active 竞争 =====
        ProcedureScoreResult bestAll = chooseBestAll(activeList, msg, scorer);
        if (bestAll == null) {
            var created = proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.UNKNOWN, msgType);
            if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
            }
            String procId = String.valueOf(created.get("procedureId"));
            return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.UNKNOWN);
        }

        Procedure bestProc = bestAll.getProcedure();
        Score bestScore = bestAll.getScore();
        ProcedureTypeEnum typeEnum = ProcedureTypeEnum.fromCode(bestProc.getProcedureTypeCode());

        // 如果该流程类型有 handler，就让 handler 负责 update；否则走旧 update_ActProcedure
        FlowHandler handler = flowRegistry.handlers().stream()
                .filter(h -> h.type() == typeEnum)
                .findFirst()
                .orElse(null);

        if (handler != null) {
            handler.applyUpdate(ueId, bestProc, bestScore, msg, nowMs, ctx);
        } else {
            proManagerService.update_ActProcedure(
                    ueId, bestProc.getProcedureId(), msgType,
                    bestScore.getPhaseIndex(), bestScore.getOrderIndex()
            );
        }

        return ProcedureMatchResult.successExisting(bestProc.getProcedureId(), typeEnum);

    }

    private ProcedureScoreResult chooseBestAll(List<Procedure> activeList, SignalingMessage msg, ScoreScorer scorer) {
        long msgTs = System.currentTimeMillis();
        int best = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScore = null;

        for (Procedure p : activeList) {
            Score s = scorer.score(p, msgTs, msg);
            if (s != null && s.getScore() > best) {
                best = s.getScore();
                bestProc = p;
                bestScore = s;
            }
        }
        if (bestProc == null || best <= 0) return null;
        return new ProcedureScoreResult(bestProc, bestScore);
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
