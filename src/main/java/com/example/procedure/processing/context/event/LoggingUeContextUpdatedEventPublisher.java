package com.example.procedure.processing.context.event;

import com.example.procedure.processing.event.InternalEventEnvelope;
import com.example.procedure.processing.event.InternalEventEnvelopes;
import com.example.procedure.processing.event.InternalEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 鍩轰簬鏃ュ織鐨?UEContext 鏇存柊浜嬩欢鍙戝竷鍣ㄣ€?
 *
 * 褰撳墠浣滅敤锛?
 * 1. 涓洪珮璐ㄩ噺鍗曚綋鎻愪緵绋冲畾鐨勪笂涓嬫枃鏇存柊瑙傛祴鐐?
 * 2. 涓烘湭鏉ヤ笂涓嬫枃鏈嶅姟鐙珛銆乷utbox銆佹秷鎭€荤嚎鍙戝竷鎻愪緵鍙浛鎹㈠疄鐜?
 * 3. 褰撳墠鍙戝竷鏃剁粺涓€鍖呰鎴愭爣鍑嗗唴閮ㄤ簨浠?envelope
 */
@Service
public class LoggingUeContextUpdatedEventPublisher implements UeContextUpdatedEventPublisher {

    /**
     * 鏃ュ織鍣ㄣ€?
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingUeContextUpdatedEventPublisher.class);

    /**
     * 鍙戝竷涓€鏉?UEContext 鏇存柊浜嬩欢銆?
     *
     * 褰撳墠瀹炵幇浼氬厛鎶婁簨浠跺寘瑁呮垚缁熶竴 envelope锛?
     * 鍐嶈緭鍑哄叕鍏卞厓鏁版嵁鍜?UEContext 鏇存柊鐗规湁瀛楁銆?
     *
     * @param event UEContext 鏇存柊浜嬩欢
     */
    @Override
    // REFACTOR STEP: INTERNAL_EVENT_ENVELOPE_UE_CONTEXT
    public void publish(UeContextUpdatedEvent event) {
        InternalEventEnvelope<UeContextUpdatedEvent> envelope = InternalEventEnvelopes.from(event);
        InternalEventMetadata meta = envelope.getMetadata();
        UeContextUpdatedEvent payload = envelope.getPayload();

        log.info(
                "Internal event published: eventId={}, eventType={}, publishedAtMs={}, correlationId={}, ueId={}, procedureId={}, procedureType={}, msgId={}, msgType={}, frameNo={}, sourceType={}, sourceName={}, reentry={}, action={}, created={}, updated={}, message={}",
                meta.getEventId(),
                meta.getEventType(),
                meta.getPublishedAtMs(),
                meta.getCorrelationId(),
                meta.getUeId(),
                meta.getProcedureId(),
                meta.getProcedureTypeCode(),
                meta.getMessageId(),
                meta.getMessageType(),
                meta.getFrameNo(),
                meta.getSourceType(),
                meta.getSourceName(),
                meta.isReentry(),
                payload.getAction(),
                payload.isCreated(),
                payload.isUpdated(),
                payload.getMessage()
        );
    }
}
