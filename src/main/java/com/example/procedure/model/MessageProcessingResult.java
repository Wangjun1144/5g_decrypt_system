package com.example.procedure.model;

import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.processing.result.ResultStatus;
import lombok.Data;

/**
 * 消息处理结果。
 *
 * 当前定位：
 * 1. 这是单条消息主链处理完成后的最终结果 DTO
 * 2. 它保留消息处理领域本身需要暴露的字段
 * 3. 同时可以转换成统一结果元数据
 */
@Data
public class MessageProcessingResult {

    /**
     * UE 标识。
     */
    private String ueId;

    /**
     * 消息类型。
     */
    private String msgType;

    /**
     * 消息分类。
     */
    private MessageCategory category;

    /**
     * 匹配到的流程 ID。
     */
    private String procedureId;

    /**
     * 匹配到的流程类型。
     */
    private String procedureType;

    /**
     * 构造消息处理结果。
     *
     * @param ueId UE 标识
     * @param msgType 消息类型
     * @param category 消息分类
     * @param procedureId 流程 ID
     * @param procedureType 流程类型
     */
    public MessageProcessingResult(
            String ueId,
            String msgType,
            MessageCategory category,
            String procedureId,
            String procedureType
    ) {
        this.ueId = ueId;
        this.msgType = msgType;
        this.category = category;
        this.procedureId = procedureId;
        this.procedureType = procedureType;
    }

    /**
     * 转换成统一结果元数据。
     *
     * 当前消息主链处理结果默认按 SUCCESS 表示，
     * 因为更细的等待/失败语义已经在主链阶段事件中表达。
     *
     * @return 统一结果元数据
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        String primaryId = procedureId != null ? procedureId : ueId;

        return new ResultMetadata(
                "MessageProcessingResult",
                ResultStatus.SUCCESS,
                primaryId,
                "msgType=" + msgType + ",category=" + category + ",procedureType=" + procedureType
        );
    }
}
