package com.example.procedure.processing.procedure.score;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.procedure.flow.ProcedureRule;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 流程评分服务。
 *
 * 当前职责：
 * 1. 计算某条消息挂接到某个已有流程上的匹配评分。
 * 2. 组合静态规则评分、时间衰减和接口/协议层额外加分。
 * 3. 为 flow 运行时提供统一的评分实现。
 *
 * 当前约束：
 * - 保持现有评分语义不变。
 * - 继续复用 {@link ProcedureRule} 作为规则驱动入口。
 * - 不在这一步引入新的评分策略框架。
 */
@Service
public class ProcedureScoringService {
    // REFACTOR STEP: PROCEDURE_RECOGNIZE_SCORE_SUBPACKAGE_REORG
    // REFACTOR STEP: PROCEDURE_ROLE_RENAME

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 计算当前消息挂接到某个流程上的匹配评分。
     */
    public Score score(Procedure proc, long msgTs, SignalingMessage msg) {
        ProcedureTypeEnum typeEnum = ProcedureTypeEnum.fromCode(proc.getProcedureTypeCode());

        ProcedureRule rule = null;
        if (typeEnum == ProcedureTypeEnum.INITIAL_ACCESS || typeEnum == ProcedureTypeEnum.XN_HANDOVER) {
            rule = new ProcedureRule(typeEnum, 60_000L);
        }

        int score = 0;
        Score result = new Score(0, -1, -1);

        if (rule != null) {
            Score ruleScore = rule.scoreForProcedure(proc, msg);
            result.setScore(ruleScore.getScore());
            result.setPhaseIndex(ruleScore.getPhaseIndex());
            result.setOrderIndex(ruleScore.getOrderIndex());
            score += result.getScore();
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
            if ("Uu".equals(iface) && "RRC".equals(layer)) {
                score += 5;
            }
            if ("N2".equals(iface) && "NGAP".equals(layer)) {
                score += 3;
            }
        } else if (typeEnum == ProcedureTypeEnum.XN_HANDOVER) {
            if ("Xn".equals(iface) && "XNAP".equals(layer)) {
                score += 5;
            }
            if ("Uu".equals(iface) && "RRC".equals(layer)) {
                score += 3;
            }
            if ("N2".equals(iface) && "NGAP".equals(layer)) {
                score += 3;
            }
        }

        result.setScore(score);
        return result;
    }

    /**
     * 解析流程最近更新时间。
     *
     * 兼容策略保持不变：
     * - 时间为空或解析失败时返回 0
     */
    private long parseTimeMillis(String timeStr) {
        if (timeStr == null) {
            return 0L;
        }
        try {
            LocalDateTime dt = LocalDateTime.parse(timeStr, FORMATTER);
            return dt.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
