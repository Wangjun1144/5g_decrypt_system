package com.example.procedure.processing.message;

import com.example.procedure.context.UeContextService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.dispatch.ProcedureDispatchService;
import com.example.procedure.processing.procedure.ProcedureRecognitionService;
import com.example.procedure.service.PendingMessageService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 规范化后的消息处理主入口。
 *
 * 当前阶段职责：
 * 1. 组织主处理链路
 * 2. 将分类、解密、流程识别、分发、pending 重试串起来
 * 3. 保持原有行为不变
 *
 * 与旧 MsgProcessing_Service 的关系：
 * - 这里是新的主实现
 * - 旧类后面会退化成兼容适配层
 *
 * 为什么这一步属于阶段 1：
 * - 文档要求先把主复杂度聚集点拆开
 * - 但暂时不改变系统功能和处理结果
 * - 这是“工程整形”，不是“业务重写”
 */
@Service
public class MessageProcessor {

    private final UeContextService ueContextService;
    private final MessageClassificationService classificationService;
    private final DecryptCoordinator decryptCoordinator;
    private final ProcedureRecognitionService procedureRecognitionService;
    private final ProcedureDispatchService procedureDispatchService;

    private final PendingMessageService pendingMessageService;


    /**
     * 这里用 @Lazy 是为了避免循环依赖：
     * MessageProcessor -> PendingRetryService -> MessageProcessor
     *
     * 在当前阶段这是可以接受的兼容式做法；
     * 后续阶段 2/3 再改成事件发布或回调接口会更优雅。
     */
    private final PendingRetryService pendingRetryService;

    public MessageProcessor(
            UeContextService ueContextService,
            MessageClassificationService classificationService,
            DecryptCoordinator decryptCoordinator,
            ProcedureRecognitionService procedureRecognitionService,
            ProcedureDispatchService procedureDispatchService,
            PendingMessageService pendingMessageService,
            @Lazy PendingRetryService pendingRetryService
    ) {
        this.ueContextService = ueContextService;
        this.classificationService = classificationService;
        this.decryptCoordinator = decryptCoordinator;
        this.procedureRecognitionService = procedureRecognitionService;
        this.procedureDispatchService = procedureDispatchService;
        this.pendingMessageService = pendingMessageService;
        this.pendingRetryService = pendingRetryService;
    }

    /**
     * 消息处理主入口。
     *
     * 处理顺序：
     * 1. 分类
     * 2. 读取 UEContext
     * 3. 若消息加密，则先做解密尝试
     * 4. 若消息属于流程相关消息，则执行流程识别
     * 5. 执行后续分发
     * 6. 上下文更新后，尝试重试 pending 解密消息
     * 7. 构造统一结果返回
     *
     * 注意：
     * - 当前阶段仍保留“递归处理回流后的消息”这一旧行为
     * - 这样风险最低，便于你先把结构拆干净
     */
    /**
     * 消息处理主入口。
     *
     * 当前阶段必须保持与原系统一致的关键行为：
     * 1. 解密 WAITING 时入 pending 队列
     * 2. 后续上下文更新后重试 pending
     * 3. 重试成功后回流并重新进入主链路
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        MessageProcessingContext context = new MessageProcessingContext(msg);

        // 第 1 步：消息分类
        classificationService.classify(context);

        // 第 2 步：读取当前 UE 上下文
        context.setUeContext(ueContextService.getContext(msg.getUeId()));

        // 第 3 步：若当前消息含加密层，则优先执行解密预处理
        DecryptAttemptResult decryptResult = decryptCoordinator.handleEncryptedMessageIfNeeded(context);
        if (decryptResult != null) {

            // 3.1 解密成功：先执行回流，再重新递归进入主链路
            if (decryptResult.getStatus() == DecryptAttemptResult.Status.OK) {
                boolean reentered = decryptCoordinator.handleDecryptSuccess(context);

                // 与原实现保持一致：
                // 只要回流成功，就对同一条消息重新执行主处理流程，
                // 让分类 / 流程识别 / 分发都吃到新的明文解析结果。
                if (reentered) {
                    return process(msg);
                }

                return buildResult(context);
            }

            // 3.2 缺材料 / 缺 key / 缺算法：必须入 pending 队列
            //
            // 这是原系统的关键行为：
            // - 当前轮无法继续解密
            // - 先把消息缓冲起来
            // - 等后续某条消息把 UEContext 更新完整后，再触发 retryPendingDecrypt(...)
            if (decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
                pendingMessageService.enqueue(
                        msg.getUeId(),
                        msg,
                        decryptResult.getReason()
                );
                return buildResult(context);
            }

            // 3.3 FAILED / SKIP：与原逻辑一致，继续走后续主流程
        }

        // 第 4 步：只有流程相关消息才进入流程识别
        if (isProcedureMessage(context.getCategory())) {
            context.setProcedureMatchResult(
                    procedureRecognitionService.recognize(msg)
            );
        }

        // 第 5 步：统一分发
        dispatchMessage(context);

        // 第 6 步：分发后上下文可能已更新（例如拿到了新的 key / 算法）
        // 因此这里重新读取最新上下文，再尝试重试该 UE 的 pending 解密消息
        UEContext refreshedContext = ueContextService.getContext(msg.getUeId());
        pendingRetryService.retryPendingDecrypt(msg.getUeId(), refreshedContext);

        // 第 7 步：统一返回结果
        return buildResult(context);
    }

    /**
     * 当前只有流程驱动类和流程辅助类消息需要进入流程识别模块。
     */
    private boolean isProcedureMessage(MessageCategory category) {
        return category == MessageCategory.PROCEDURE_DRIVING
                || category == MessageCategory.PROCEDURE_AUX;
    }

    /**
     * 统一执行消息分发。
     *
     * 说明：
     * - procedureId / procedureTypeCode 的拼装细节不应该散落在主流程里
     * - 因此这里集中做一次转换
     */
    private void dispatchMessage(MessageProcessingContext context) {
        String procedureId = null;
        String procedureTypeCode = null;

        if (context.getProcedureMatchResult() != null
                && context.getProcedureMatchResult().getStatus() == 0) {

            procedureId = context.getProcedureMatchResult().getProcedureId();

            ProcedureTypeEnum procedureType = context.getProcedureMatchResult().getProcedureType();
            if (procedureType != null) {
                procedureTypeCode = procedureType.getCode();
            }
        }

        procedureDispatchService.dispatch(
                context.getMessage(),
                context.getCategory(),
                procedureId,
                procedureTypeCode
        );
    }

    /**
     * 统一构造返回结果，保证不同分支出口的结果格式一致。
     */
    private MessageProcessingResult buildResult(MessageProcessingContext context) {
        String procedureId = null;
        String procedureTypeCode = null;

        if (context.getProcedureMatchResult() != null
                && context.getProcedureMatchResult().getStatus() == 0) {

            procedureId = context.getProcedureMatchResult().getProcedureId();

            ProcedureTypeEnum procedureType = context.getProcedureMatchResult().getProcedureType();
            if (procedureType != null) {
                procedureTypeCode = procedureType.getCode();
            }
        }

        SignalingMessage msg = context.getMessage();

        return new MessageProcessingResult(
                msg.getUeId(),
                msg.getMsgType(),
                context.getCategory(),
                procedureId,
                procedureTypeCode
        );
    }
}