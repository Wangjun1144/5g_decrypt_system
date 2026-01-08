package com.example.procedure.service;

import com.example.procedure.flow.ProcedureScoreResult;
import com.example.procedure.model.*;
import com.example.procedure.rule.InitialAccessKeyBits;
import com.example.procedure.rule.InitialAccessPhases;
import com.example.procedure.rule.PhaseDef;
import com.example.procedure.rule.ProcedureRule;
import com.example.procedure.util.ProcedureProgressUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProClassify_Service1 {

    private final ProManager_Service proManagerService;

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

        // ===== 0) IA 触发器优先：先归并已有 IA，接不住才新建 IA =====
        if (isIaTrigger(msg)) {

            List<Procedure> activeList = proManagerService.listActiveProcedures(ueId);

            // 0.1 优先只在 IA 流程里找 best（避免 UNKNOWN 抢走触发器）
            ProcedureScoreResult iaBest = chooseBestIaProcedure(activeList, msg);

            if (iaBest != null && iaBest.getScore() != null
                    && iaBest.getScore().getScore() >= IA_MERGE_THRESHOLD) {

                Procedure best = iaBest.getProcedure();
                Score bestScore = iaBest.getScore();

                // —— 归并到已有 IA：执行 IA 的乱序增强更新逻辑 ——
                applyIaUpdate(ueId, best, bestScore, msgType, nowMs);

                return ProcedureMatchResult.successExisting(best.getProcedureId(), ProcedureTypeEnum.INITIAL_ACCESS);
            }

            // 0.2 接不住 -> 新建 IA（注意：新建 IA 在 add_ActProcedure 里要保持 -1/-1）
            Map<String, Object> created =
                    proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.INITIAL_ACCESS, msgType);

            if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                return ProcedureMatchResult.error("failed to create IA procedure");
            }

            String procId = String.valueOf(created.get("procedureId"));
            return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.INITIAL_ACCESS);
        }

        // ===== 1) 非 IA 触发器：走你原来的逻辑 =====

        // 1. 起始消息（你原来的 tryStartProcedures 只认 RRCSetupComplete，可以保留也可以删除）
        ProcedureMatchResult startResult = tryStartProcedures(ueId, msg);
        if (startResult != null && startResult.getStatus() == 0) {
            return startResult;
        }

        // 2. 在已有流程中选择最匹配的一个
        List<Procedure> activeList = proManagerService.listActiveProcedures(ueId);
        if (activeList == null || activeList.isEmpty()) {
            // 无流程 -> 新建 UNKNOWN
            Map<String, Object> created =
                    proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.UNKNOWN, msgType);

            if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
            }

            String procId = String.valueOf(created.get("procedureId"));
            return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.UNKNOWN);
        }

        ProcedureScoreResult bestResult = chooseBestProcedure(activeList, msg);
        if (bestResult == null) {
            // 无匹配 -> 新建 UNKNOWN
            Map<String, Object> created =
                    proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.UNKNOWN, msgType);

            if (created == null || (int) created.getOrDefault("status", 1) != 0) {
                return ProcedureMatchResult.error("failed to create UNKNOWN procedure");
            }

            String procId = String.valueOf(created.get("procedureId"));
            return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.UNKNOWN);
        }

        Procedure best = bestResult.getProcedure();
        Score bestScore = bestResult.getScore();
        ProcedureTypeEnum typeEnum = ProcedureTypeEnum.fromCode(best.getProcedureTypeCode());

        // ===== 乱序增强：只对 IA 用 endSeen/keyMask/单调推进/延迟关闭 =====
        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS) {
            applyIaUpdate(ueId, best, bestScore, msgType, nowMs);
        } else {
            // 非 IA 维持原逻辑
            proManagerService.update_ActProcedure(
                    ueId,
                    best.getProcedureId(),
                    msgType,
                    bestScore.getPhaseIndex(),
                    bestScore.getOrderIndex()
            );
        }

        return ProcedureMatchResult.successExisting(best.getProcedureId(), typeEnum);
    }

    // ========================= IA 相关新增/抽取 =========================

    /** IA 触发器：phaseStart / keyBit / endMessage */
    private boolean isIaTrigger(SignalingMessage msg) {
        String t = msg.getMsgType();
        if (t == null) return false;

        // END 也可能是唯一抓到的东西
        if (InitialAccessPhases.isEndMessage(t)) return true;

        // phaseStart 的都算触发器（更稳）
        PhaseDef.PhaseLocation loc = InitialAccessPhases.locate(t);
        if (loc != null && loc.isPhaseStart()) {
            return InitialAccessPhases.hasValidPayloadForPhaseStart(msg, loc.getPhaseIndex());
        }

        // 你要求“任意关键消息也能启动”
        return InitialAccessKeyBits.bitForMsgType(t) != 0;
    }

    /** 只在 IA 活跃流程里选 best（避免 UNKNOWN 抢触发器） */
    private ProcedureScoreResult chooseBestIaProcedure(List<Procedure> activeList, SignalingMessage msg) {
        if (activeList == null || activeList.isEmpty()) return null;

        long msgTs = System.currentTimeMillis();

        int bestScore = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScoreObj = null;

        for (Procedure p : activeList) {
            if (ProcedureTypeEnum.fromCode(p.getProcedureTypeCode()) != ProcedureTypeEnum.INITIAL_ACCESS) {
                continue;
            }
            Score s = scoreProcedure(p, msgTs, msg);
            if (s != null && s.getScore() > bestScore) {
                bestScore = s.getScore();
                bestProc = p;
                bestScoreObj = s;
            }
        }

        if (bestProc == null) return null;
        return new ProcedureScoreResult(bestProc, bestScoreObj);
    }

    /** IA 更新逻辑抽出来，方便复用（归并路径/普通匹配路径共用） */
    private void applyIaUpdate(String ueId, Procedure best, Score bestScore, String msgType, long nowMs) {

        // 1) END 只标记，不立刻结束
        if (InitialAccessPhases.isEndMessage(msgType)) {
            best.setEndSeen(true);
            if (best.getEndSeenAtMs() == 0L) {
                best.setEndSeenAtMs(nowMs);
            }
        }

        // 2) keyMask（关键消息位图）
        int bit = InitialAccessKeyBits.bitForMsgType(msgType);
        if (bit != 0) {
            best.setKeyMask(best.getKeyMask() | bit);
        }

        // 3) lastPhase/lastOrder 单调推进
        ProcedureProgressUtil.advanceMonotonic(best, bestScore.getPhaseIndex(), bestScore.getOrderIndex());

        // 4) 持久化（扩展字段写回 Redis）
        proManagerService.update_ActProcedureEx(
                ueId,
                best.getProcedureId(),
                msgType,
                best.getLastPhaseIndex(),
                best.getLastOrderIndex(),
                best.isEndSeen(),
                best.getEndSeenAtMs(),
                best.getKeyMask()
        );

        // 5) 真正结束条件：endSeen + keysEnough 或超时（你的现有逻辑）
        if (ProcedureCloseDecider11.isReadyToClose(best, nowMs)) {
            proManagerService.end_Procedure(ueId, best.getProcedureId());
        }
    }

    // ========================= 你原来的逻辑（基本保留） =========================

    private ProcedureScoreResult chooseBestProcedure(List<Procedure> activeList, SignalingMessage msg) {
        if (activeList == null || activeList.isEmpty()) {
            return null;
        }

        long msgTs = System.currentTimeMillis();

        int bestScore = Integer.MIN_VALUE;
        Procedure bestProc = null;
        Score bestScoreObj = null;

        for (Procedure p : activeList) {
            Score s = scoreProcedure(p, msgTs, msg);
            if (s != null && s.getScore() > bestScore) {
                bestScore = s.getScore();
                bestProc = p;
                bestScoreObj = s;
            }
        }

        if (bestProc == null || bestScore <= 0) {
            return null;
        }

        return new ProcedureScoreResult(bestProc, bestScoreObj);
    }

    private ProcedureMatchResult tryStartProcedures(String ueId, SignalingMessage msg) {
        // 保留你原来逻辑（只认 RRCSetupComplete），即使不改也不影响触发器新能力
        InitialAccessPhases.StartType st = InitialAccessPhases.checkStartType(msg);
        if (st != InitialAccessPhases.StartType.CONFIRMED_START) {
            return null;
        }

        Map<String, Object> created =
                proManagerService.add_ActProcedure(ueId, ProcedureTypeEnum.INITIAL_ACCESS, msg.getMsgType());
        if (created == null || (int) created.getOrDefault("status", 1) != 0) {
            return ProcedureMatchResult.error("failed to create IA procedure");
        }

        String procId = String.valueOf(created.get("procedureId"));
        return ProcedureMatchResult.successNew(procId, ProcedureTypeEnum.INITIAL_ACCESS);
    }

    private Score scoreProcedure(Procedure proc, long msgTs, SignalingMessage msg) {

        ProcedureTypeEnum typeEnum = ProcedureTypeEnum.fromCode(proc.getProcedureTypeCode());

        ProcedureRule rule = null;
        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS) {
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
            if (typeEnum == ProcedureTypeEnum.UNKNOWN) {
                score += 5;
            }
        }

        long lastUpdateMillis = parseTimeMillis(proc.getLastUpdateTime());
        long diff = Math.abs(msgTs - lastUpdateMillis);

        if (diff <= 1_000L) {
            score += 10;
        } else if (diff <= 10_000L) {
            score += 5;
        } else if (diff <= 60_000L) {
            score += 1;
        }

        if (rule != null && rule.getMaxIdleMillis() > 0 && diff > rule.getMaxIdleMillis()) {
            score -= 20;
        }

        String iface = msg.getIface();
        String layer = msg.getProtocolLayer();

        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS) {
            if ("Uu".equals(iface) && "RRC".equals(layer)) score += 5;
            if ("N2".equals(iface) && "NGAP".equals(layer)) score += 3;
        } else if (typeEnum == ProcedureTypeEnum.RRC_REESTABLISH) {
            if ("Uu".equals(iface) && "RRC".equals(layer)) score += 5;
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
