package com.example.procedure.processing.message;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.support.logging.StageLogRefs;
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
@Component
public class MessageProcessingResultFactory {

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
    public String summary(MessageProcessingResult result) {
        if (result == null) {
            return "result:null";
        }

        ResultMetadata metadata = result.toResultMetadata();

        return "resultType=" + metadata.getResultType()
                + ",status=" + metadata.getStatus()
                + ",primaryId=" + StageLogRefs.safe(metadata.getPrimaryId())
                + ",message=" + StageLogRefs.safe(metadata.getMessage());
    }
}
