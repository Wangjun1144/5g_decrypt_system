package com.example.procedure.service;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DEMO 版流程调度器：
 * 根据流程类型/消息类别，后续可以扩展调用解密、解析、上下文更新等。
 * 现在先只打印日志，方便你验证整体链路。
 */
@Service
public class ProDispatcher_Service {
    private static final Logger log = LoggerFactory.getLogger(ProDispatcher_Service.class);
    private final UEContextService ueContextService;

    public ProDispatcher_Service(UEContextService ueContextService){
        this.ueContextService = ueContextService;
    }

    public void dispatch(SignalingMessage msg,
                         MessageCategory category,
                         String procedureId,
                         String procedureType){
        // DEMO：目前先只打一个日志，确认调度信息是否正确
        log.info("Dispatch msg. ueId={}, msgType={}, category={}, procedureType={}, procedureId={}",
                msg.getUeId(), msg.getMsgType(), category, procedureType, procedureId);

        // 仅作为示例：对 IA 流程驱动信令，顺带更新一下 UE 上下文
        if ("IA".equalsIgnoreCase(procedureType) && category == MessageCategory.PROCEDURE_DRIVING) {
            ueContextService.updateOnInitialAccess(msg, procedureId);
        }

        // TODO：后续扩展点举例：
        // 1. 所有消息：统一原始入库 / 写 Kafka / 写文件
        // 2. 根据流程类型做特定处理：
        //    if ("Initial Access".equals(procedureType) || "IA".equals(procedureTypeCode)) { ... }
        // 3. 驱动 UE 上下文更新、密钥管理、解密、协议解析等
    }
}
