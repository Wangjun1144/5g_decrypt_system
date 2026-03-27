package com.example.procedure.processing.message.result;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.result.ResultMetadata;
import org.springframework.stereotype.Component;

/**
 * MessageProcessingResult 工厂。
 *
 * 当前职责：
 * 1. 根据主链共享上下文构造统一处理结果
 * 2. 统一提供处理结果的摘要文本，供主链日志使用
 *
 * 当前这一步的重点：
 * - 结果摘要不再只依赖 MessageProcessingResult 自己的字段拼接
 * - 开始接入统一 ResultMetadata 契约
 */
/**
 * Assembles {@link MessageProcessingResult} objects from the shared message context.
 *
 * Current responsibilities:
 * 1. Build the final outward-facing result for the message main chain.
 * 2. Produce a stable summary string for logging and internal observability.
 * 3. Normalize summary output through the {@link ResultMetadata} contract.
 */
@Component
public class MessageProcessingResultAssembler {
    // REFACTOR STEP: MESSAGE_ROLE_RENAME
    // REFACTOR STEP: MESSAGE_RESULT_SUBPACKAGE_REORG
    // REFACTOR STEP: MESSAGE_ROLE_RENAME_PHASE2

    /**
     * 根据主链共享上下文构造统一返回结果。
     *
     * @param context 当前处理上下文
     * @return 消息处理结果
     */
    public MessageProcessingResult build(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();

        return new MessageProcessingResult(
                msg.getUeId(),
                msg.getMsgType(),
                context.getCategory(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode()
        );
    }

    /**
     * 生成处理结果摘要文本。
     *
     * 这里开始统一基于 ResultMetadata 输出基础结果摘要，
     * 这样以后其他结果对象也可以复用同样的摘要风格。
     *
     * @param result 消息处理结果
     * @return 结果摘要文本
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    // REFACTOR STEP: MESSAGE_NAMING_AND_COMMENT_CLEANUP
    public String summary(MessageProcessingResult result) {
        return summarize(result).toLogString();
    }

    /**
     * Builds a typed summary view so reporting code can consume structured result data.
     */
    public MessageProcessingSummary summarize(MessageProcessingResult result) {
        if (result == null) {
            return new MessageProcessingSummary(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ResultMetadata("MessageProcessingResult", null, null, "result:null")
            );
        }

        ResultMetadata metadata = result.toResultMetadata();

        return new MessageProcessingSummary(
                result.getUeId(),
                result.getMsgType(),
                result.getCategory(),
                result.getProcedureId(),
                result.getProcedureType(),
                metadata
        );
    }
}
