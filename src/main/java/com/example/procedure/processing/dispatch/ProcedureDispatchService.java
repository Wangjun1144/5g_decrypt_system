package com.example.procedure.processing.dispatch;

import com.example.procedure.processing.context.UeContextService;
import com.example.procedure.processing.context.UeContextUpdateRequest;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.MessageSourceType;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 娴佺▼鍒嗗彂鏈嶅姟銆?
 *
 * 褰撳墠闃舵瀹氫綅锛?
 * - 杩欐槸鈥滄祦绋嬪鐞嗛樁娈碘€濅腑鐨勫垎鍙戠粍浠?
 * - 璐熻矗鎵挎帴娴佺▼璇嗗埆鍚庣殑缁熶竴鍒嗗彂鍔ㄤ綔
 * - 褰撳墠鍏堜繚鐣欐棫绯荤粺宸叉湁琛屼负锛屼笉寮曞叆鏂扮殑涓氬姟鍒嗘敮
 * - 鍚屾椂涓哄悗缁簨浠堕┍鍔ㄦ紨杩涙敹鍙ｅ唴閮ㄤ簨浠跺彂甯冭竟鐣?
 */
@Service
public class ProcedureDispatchService {

    /**
     * 鏃ュ織鍣ㄣ€?
     */
    private static final Logger log = LoggerFactory.getLogger(ProcedureDispatchService.class);

    /**
     * UE 涓婁笅鏂囨湇鍔°€?
     */
    private final UeContextService ueContextService;

    /**
     * 娴佺▼鍒嗗彂浜嬩欢鍙戝竷鍣ㄣ€?
     */
    private final ProcedureDispatchEventPublisher eventPublisher;

    /**
     * 鏋勯€犳祦绋嬪垎鍙戞湇鍔°€?
     *
     * @param ueContextService UE 涓婁笅鏂囨湇鍔?
     * @param eventPublisher 娴佺▼鍒嗗彂浜嬩欢鍙戝竷鍣?
     */
    public ProcedureDispatchService(
            UeContextService ueContextService,
            ProcedureDispatchEventPublisher eventPublisher
    ) {
        this.ueContextService = ueContextService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 鏂扮殑姝ｅ紡鍏ュ彛锛氱洿鎺ユ帴鏀朵富閾句笂涓嬫枃銆?
     *
     * @param context 褰撳墠涓婚摼涓婁笅鏂?
     */
    public ProcedureDispatchOutcome dispatch(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        MessageCategory category = context.getCategory();
        String procedureId = context.getMatchedProcedureId();
        String procedureTypeCode = context.getMatchedProcedureTypeCode();
        boolean ueContextUpdated = false;

        log.info(
                "Dispatch msg. ueId={}, msgType={}, category={}, procedureType={}, procedureId={}, sourceType={}, sourceName={}, correlationId={}, reentry={}",
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureTypeCode,
                procedureId,
                context.getSourceType(),
                context.getSourceName(),
                context.getCorrelationId(),
                context.isReentry()
        );

        if (shouldUpdateInitialAccessContext(category, procedureTypeCode)) {
            // REFACTOR STEP: UE_CONTEXT_BOUNDARY
            ueContextService.process(new UeContextUpdateRequest(
                    msg,
                    procedureId,
                    context.getSourceType(),
                    context.getSourceName(),
                    context.getCorrelationId(),
                    context.isReentry()
            ));
            ueContextUpdated = true;
        }

        publishDispatchEvent(context);
        return ProcedureDispatchOutcome.of(true, ueContextUpdated, procedureId, procedureTypeCode);
    }

    /**
     * 鍏煎鏃ц皟鐢ㄦ柟寮忥紝閬垮厤涓€娆℃€т慨鏀硅繃澶氳皟鐢ㄦ柟銆?
     *
     * @param msg 褰撳墠娑堟伅
     * @param category 褰撳墠娑堟伅鍒嗙被
     * @param procedureId 褰撳墠娴佺▼ ID
     * @param procedureTypeCode 褰撳墠娴佺▼绫诲瀷缂栫爜
     */
    public ProcedureDispatchOutcome dispatch(
            SignalingMessage msg,
            MessageCategory category,
            String procedureId,
            String procedureTypeCode
    ) {
        boolean ueContextUpdated = false;
        log.info(
                "Dispatch msg. ueId={}, msgType={}, category={}, procedureType={}, procedureId={}",
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureTypeCode,
                procedureId
        );

        if (shouldUpdateInitialAccessContext(category, procedureTypeCode)) {
            ueContextService.updateOnInitialAccess(msg, procedureId);
            ueContextUpdated = true;
        }

        ProcedureDispatchedEvent event = new ProcedureDispatchedEvent(
                null,
                msg.getUeId(),
                procedureId,
                procedureTypeCode,
                category,
                null,
                null,
                false,
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                msg.getTimestamp(),
                "procedure-dispatch"
        );
        eventPublisher.publish(event);
        return ProcedureDispatchOutcome.of(true, ueContextUpdated, procedureId, procedureTypeCode);
    }

    /**
     * 鍙戝竷涓€鏉℃祦绋嬪垎鍙戜簨浠躲€?
     *
     * @param context 褰撳墠涓婚摼涓婁笅鏂?
     */
    private void publishDispatchEvent(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();

        ProcedureDispatchedEvent event = new ProcedureDispatchedEvent(
                context.getCorrelationId(),
                msg.getUeId(),
                context.getMatchedProcedureId(),
                context.getMatchedProcedureTypeCode(),
                context.getCategory(),
                context.getSourceType(),
                context.getSourceName(),
                context.isReentry(),
                msg.getMsgId(),
                msg.getMsgType(),
                msg.getFrameNo(),
                msg.getTimestamp(),
                "procedure-dispatch"
        );

        eventPublisher.publish(event);
    }

    /**
     * 鍒ゆ柇褰撳墠娑堟伅鏄惁闇€瑕佽Е鍙?IA 涓婁笅鏂囨洿鏂般€?
     *
     * @param category 褰撳墠娑堟伅鍒嗙被
     * @param procedureTypeCode 褰撳墠娴佺▼绫诲瀷缂栫爜
     * @return true 琛ㄧず闇€瑕佹洿鏂?
     */
    private boolean shouldUpdateInitialAccessContext(
            MessageCategory category,
            String procedureTypeCode
    ) {
        return category == MessageCategory.PROCEDURE_DRIVING
                && "IA".equalsIgnoreCase(procedureTypeCode);
    }
}
