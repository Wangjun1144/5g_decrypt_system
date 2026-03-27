package com.example.procedure.infrastructure.decrypt.gateway;

/**
 * Transport-neutral decrypt request contract.
 */
public class DecryptRequest {

    private final String messageId;
    private final String ueId;
    private final String contextRef;
    private final String layer;
    private final String encKey;
    private final String intKey;
    private final String encAlgo;
    private final String intAlgo;
    private final int count;
    private final int bearer;
    private final String direction;
    private final String ciphertext;
    private final String mac;
    private final int dataLength;

    /**
     * Create one decrypt request.
     *
     * @param messageId message identifier
     * @param ueId UE identifier
     * @param contextRef external decrypt-context reference
     * @param layer logical layer to decrypt, for example NAS or AS
     * @param encKey ciphering key
     * @param intKey integrity key
     * @param encAlgo ciphering algorithm name
     * @param intAlgo integrity algorithm name
     * @param count protocol counter value
     * @param bearer bearer identifier
     * @param direction traffic direction
     * @param ciphertext encrypted payload
     * @param mac message authentication code
     * @param dataLength optional explicit data-length hint
     */
    public DecryptRequest(
            String messageId,
            String ueId,
            String contextRef,
            String layer,
            String encKey,
            String intKey,
            String encAlgo,
            String intAlgo,
            int count,
            int bearer,
            String direction,
            String ciphertext,
            String mac,
            int dataLength
    ) {
        this.messageId = messageId;
        this.ueId = ueId;
        this.contextRef = contextRef;
        this.layer = layer;
        this.encKey = encKey;
        this.intKey = intKey;
        this.encAlgo = encAlgo;
        this.intAlgo = intAlgo;
        this.count = count;
        this.bearer = bearer;
        this.direction = direction;
        this.ciphertext = ciphertext;
        this.mac = mac;
        this.dataLength = dataLength;
    }

    public static DecryptRequest of(
            String messageId,
            String ueId,
            String contextRef,
            String layer,
            String encKey,
            String intKey,
            String encAlgo,
            String intAlgo,
            int count,
            int bearer,
            String direction,
            String ciphertext,
            String mac,
            int dataLength
    ) {
        return new DecryptRequest(
                messageId, ueId, contextRef, layer,
                encKey, intKey, encAlgo, intAlgo,
                count, bearer, direction, ciphertext, mac, dataLength
        );
    }

    public String getMessageId() { return messageId; }
    public String getUeId() { return ueId; }
    public String getContextRef() { return contextRef; }
    public String getLayer() { return layer; }
    public String getEncKey() { return encKey; }
    public String getIntKey() { return intKey; }
    public String getEncAlgo() { return encAlgo; }
    public String getIntAlgo() { return intAlgo; }
    public int getCount() { return count; }
    public int getBearer() { return bearer; }
    public String getDirection() { return direction; }
    public String getCiphertext() { return ciphertext; }
    public String getMac() { return mac; }
    public int getDataLength() { return dataLength; }
}
