package com.example.procedure.processing.procedure;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.rule.ProcedureRule;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 流程评分服务。
 *
 * 设计目的：
 * 1. 将流程评分逻辑从旧 ProClassify_Service 中拆出来
 * 2. 让“流程识别 / 流程执行 / 流程评分”三类职责逐步分离
 * 3. 为后续继续演进出更清晰的流程领域服务打基础
 *
 * 当前职责：
 * - 根据当前消息和已有流程，计算该消息挂接到该流程的评分
 *
 * 当前阶段约束：
 * - 不改变现有评分规则
 * - 不改变对 ProcedureRule 的使用方式
 * - 不改变时间衰减、协议层加分、UNKNOWN 兜底加分等现有行为
 *
 * 这一步的意义：
 * - ProClassify_Service 进一步瘦身
 * - ScoreScorer 的真正实现开始从旧服务中迁出
 * - 后续如果要做流程评分策略演进，会更容易落地
 */
@Service
public class ProcedureScoreService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 计算当前消息与某个流程之间的匹配评分。
     *
     * 当前评分逻辑保持原样：
     * 1. 若流程类型存在对应 ProcedureRule，则优先使用 rule.scoreForProcedure(...)
     * 2. 根据消息时间与流程最近更新时间的距离做衰减/加分
     * 3. 根据 iface / protocolLayer 做额外加分
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
     * 当前仍保持原有容错语义：
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
