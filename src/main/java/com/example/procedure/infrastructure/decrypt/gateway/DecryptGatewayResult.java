package com.example.procedure.infrastructure.decrypt.gateway;

/**
 * Transport-neutral decrypt response contract.
 */
public class DecryptGatewayResult {

    private final String decryptStatus;
    private final String plainData;
    private final String plainMac;
    private final String errorMessage;

    /**
     * Create one decrypt gateway result.
     *
     * @param decryptStatus external decrypt status code
     * @param plainData decrypted plaintext payload
     * @param plainMac decrypted or validated MAC output
     * @param errorMessage optional transport or decrypt error message
     */
    public DecryptGatewayResult(
            String decryptStatus,
            String plainData,
            String plainMac,
            String errorMessage
    ) {
        this.decryptStatus = decryptStatus;
        this.plainData = plainData;
        this.plainMac = plainMac;
        this.errorMessage = errorMessage;
    }

    public static DecryptGatewayResult of(
            String decryptStatus,
            String plainData,
            String plainMac,
            String errorMessage
    ) {
        return new DecryptGatewayResult(decryptStatus, plainData, plainMac, errorMessage);
    }

    public String getDecryptStatus() { return decryptStatus; }
    public String getPlainData() { return plainData; }
    public String getPlainMac() { return plainMac; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * @return true when the external decrypt capability reported success
     */
    public boolean isSuccess() {
        return "DECRYPT_SUCCESS".equals(decryptStatus);
    }
}
