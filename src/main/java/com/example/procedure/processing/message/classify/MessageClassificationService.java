package com.example.procedure.processing.message.classify;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import org.springframework.stereotype.Service;

/**
 * 消息分类服务。
 *
 * 职责：
 * - 根据消息内容判定 MessageCategory
 *
 * 阶段 1 目标：
 * - 把“分类动作”从消息主链协调器中显式抽出来
 * - 先只做一层包装，不改变原有分类规则
 *
 * 阶段 2 可继续演进为：
 * - 分类规则组合器
 * - 可观测的分类决策输出
 * - 分类失败/未知类别审计
 */
@Service
public class MessageClassificationService {
    // REFACTOR STEP: MESSAGE_CLASSIFY_SUBPACKAGE_REORG

    private final MessageCategoryClassifier classifier;

    public MessageClassificationService(MessageCategoryClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * 对当前消息执行分类，并写回上下文。
     */
    public MessageClassificationOutcome classify(MessageProcessingContext context) {
        MessageCategory category = classifier.classify(context.getMessage());
        context.setCategory(category);
        return MessageClassificationOutcome.of(category);
    }
}
