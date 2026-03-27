package com.example.procedure.processing.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.MessageSourceType;

/**
 * UEContext 鏇存柊璇锋眰銆?
 *
 * 褰撳墠鐢ㄩ€旓細
 * 1. 缁熶竴鎵挎帴涓€娆?UEContext 鏇存柊鎵€闇€鐨勮緭鍏?
 * 2. 璁╀笂涓嬫枃鏇存柊閾句笉鍐嶅彧鏆撮湶瑁告秷鎭?+ procedureId
 * 3. 涓哄悗缁笂涓嬫枃鏈嶅姟鎷嗗垎銆佸紓姝ユ洿鏂般€佷簨浠跺洖鏀鹃鐣欑ǔ瀹氳緭鍏ユā鍨?
 */
public class UeContextUpdateRequest {

    /**
     * 褰撳墠娑堟伅銆?
     */
    private final SignalingMessage message;

    /**
     * 褰撳墠鍏宠仈鐨勬祦绋?ID銆?
     */
    private final String procedureId;

    /**
     * 鏉ユ簮绫诲瀷銆?
     */
    private final MessageSourceType sourceType;

    /**
     * 鏉ユ簮鍚嶇О銆?
     */
    private final String sourceName;

    /**
     * 鍏宠仈 ID銆?
     */
    private final String correlationId;

    /**
     * 鏄惁鍥炴祦銆?
     */
    private final boolean reentry;

    /**
     * 鏋勯€?UEContext 鏇存柊璇锋眰銆?
     *
     * @param message 褰撳墠娑堟伅
     * @param procedureId 褰撳墠娴佺▼ ID
     * @param sourceType 鏉ユ簮绫诲瀷
     * @param sourceName 鏉ユ簮鍚嶇О
     * @param correlationId 鍏宠仈 ID
     * @param reentry 鏄惁鍥炴祦
     */
    public UeContextUpdateRequest(
            SignalingMessage message,
            String procedureId,
            MessageSourceType sourceType,
            String sourceName,
            String correlationId,
            boolean reentry
    ) {
        this.message = message;
        this.procedureId = procedureId;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.correlationId = correlationId;
        this.reentry = reentry;
    }

    /**
     * 鑾峰彇褰撳墠娑堟伅銆?
     *
     * @return 褰撳墠娑堟伅
     */
    public SignalingMessage getMessage() {
        return message;
    }

    /**
     * 鑾峰彇褰撳墠娴佺▼ ID銆?
     *
     * @return 褰撳墠娴佺▼ ID
     */
    public String getProcedureId() {
        return procedureId;
    }

    /**
     * 鑾峰彇鏉ユ簮绫诲瀷銆?
     *
     * @return 鏉ユ簮绫诲瀷
     */
    public MessageSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 鑾峰彇鏉ユ簮鍚嶇О銆?
     *
     * @return 鏉ユ簮鍚嶇О
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * 鑾峰彇鍏宠仈 ID銆?
     *
     * @return 鍏宠仈 ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 鍒ゆ柇鏄惁鍥炴祦銆?
     *
     * @return true 琛ㄧず鍥炴祦
     */
    public boolean isReentry() {
        return reentry;
    }
}
