package com.example.procedure.processing.message;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.StageLogRefs;
import org.springframework.stereotype.Component;

/**
 * MessageProcessingResult 工厂。
 *
 * 当前职责：
 * 1. 根据主链共享上下文构造统一处理结果
 * 2. 统一提供处理结果的摘要文本，供主链日志使用
 *
 * 第 27 小步的重点：
 * - 不只负责 build(...)
 * - 也负责把 MessageProcessingResult 转成稳定的摘要字符串
 *
 * 这样做的意义：
 * - MessageProcessor 不再直接关心结果对象的字段拼接方式
 * - “结果构造”和“结果摘要”统一收口到一个位置
 * - 后续如果结果字段扩展，修改点更集中
 */
@Component
public class MessageProcessingResultFactory {

    /**
     * 根据主链共享上下文构造统一返回结果。
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
     * 生成处理结果的摘要文本。
     *
     * 当前统一格式：
     * category={...},procedureId={...},procedureType={...}
     *
     * 这里不重复输出 msgId / ueId / msgType，
     * 因为这些在外层日志里已经通过 StageLogRefs 输出。
     */
    public String summary(MessageProcessingResult result) {
        if (result == null) {
            return "result:null";
        }

        return "category=" + result.getCategory()
                + ",procedureId=" + StageLogRefs.safe(result.getProcedureId())
                + ",procedureType=" + StageLogRefs.safe(result.getProcedureType());
    }
}
