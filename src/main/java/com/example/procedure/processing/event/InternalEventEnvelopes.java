package com.example.procedure.processing.event;

import com.example.procedure.processing.context.event.UeContextUpdatedEvent;
import com.example.procedure.processing.binding.event.BindingResolvedEvent;
import com.example.procedure.processing.dispatch.ProcedureDispatchedEvent;
import com.example.procedure.processing.message.event.MessageStageEvent;

/**
 * 鍐呴儴浜嬩欢 envelope 宸ュ巶銆?
 *
 * 褰撳墠鐢ㄩ€旓細
 * 1. 缁熶竴鎶婄幇鏈変簨浠跺璞¤浆鎹㈡垚鏍囧噯 envelope
 * 2. 鎶婂叕鍏卞厓鏁版嵁鐨勬嫾瑁呴€昏緫闆嗕腑鍒颁竴涓湴鏂?
 * 3. 鍑忓皯 publisher 涓噸澶嶆嫾瀛楁鐨勬牱鏉夸唬鐮?
 */
public final class InternalEventEnvelopes {

    /**
     * 宸ュ叿绫讳笉鍏佽瀹炰緥鍖栥€?
     */
    private InternalEventEnvelopes() {
    }

    /**
     * 鎶婃秷鎭樁娈典簨浠跺寘瑁呮垚鏍囧噯 envelope銆?
     *
     * @param event 娑堟伅闃舵浜嬩欢
     * @return 鏍囧噯 envelope
     */
    public static InternalEventEnvelope<MessageStageEvent> from(MessageStageEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 鎶婃祦绋嬪垎鍙戜簨浠跺寘瑁呮垚鏍囧噯 envelope銆?
     *
     * @param event 娴佺▼鍒嗗彂浜嬩欢
     * @return 鏍囧噯 envelope
     */
    public static InternalEventEnvelope<ProcedureDispatchedEvent> from(ProcedureDispatchedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                event.getProcedureTypeCode(),
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 鎶?binding 浜嬩欢鍖呰鎴愭爣鍑?envelope銆?
     *
     * 娉ㄦ剰锛?
     * - 褰撳墠 binding 浜嬩欢杩樻病鏈夋祦绋?ID銆佹祦绋嬬被鍨嬨€佹秷鎭椂闂存埑
     * - 杩欎簺瀛楁鍏堜繚鐣欎负绌猴紝鍚庣画濡傛灉 binding 闃舵琛ヤ簡涓婁笅鏂囧啀缁х画濉厖
     *
     * @param event binding 浜嬩欢
     * @return 鏍囧噯 envelope
     */
    public static InternalEventEnvelope<BindingResolvedEvent> from(BindingResolvedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                null,
                null,
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                null,
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                System.currentTimeMillis()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }

    /**
     * 鎶?UEContext 鏇存柊浜嬩欢鍖呰鎴愭爣鍑?envelope銆?
     *
     * 杩欐牱鍋氫箣鍚庯紝UEContext 鏇存柊閾惧氨鍜?message / dispatch / binding 涓€鏍凤紝
     * 閮借蛋缁熶竴鐨勫唴閮ㄤ簨浠跺厓鏁版嵁缁撴瀯銆?
     *
     * @param event UEContext 鏇存柊浜嬩欢
     * @return 鏍囧噯 envelope
     */
    // REFACTOR STEP: INTERNAL_EVENT_ENVELOPE_UE_CONTEXT
    public static InternalEventEnvelope<UeContextUpdatedEvent> from(UeContextUpdatedEvent event) {
        InternalEventMetadata metadata = new InternalEventMetadata(
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getUeId(),
                event.getProcedureId(),
                null,
                event.getMessageId(),
                event.getMessageType(),
                event.getFrameNo(),
                event.getMessageTimestamp(),
                event.getSourceType(),
                event.getSourceName(),
                event.isReentry(),
                event.getPublishedAtMs()
        );
        return new InternalEventEnvelope<>(metadata, event);
    }
}
