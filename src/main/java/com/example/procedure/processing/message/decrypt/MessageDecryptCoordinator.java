package com.example.procedure.processing.message.decrypt;

import com.example.procedure.infrastructure.decrypt.gateway.DecryptGatewayResult;
import com.example.procedure.infrastructure.decrypt.gateway.DecryptRequest;
import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates decrypt decisions for one message.
 *
 * The coordinator now focuses on:
 * 1. Deciding whether decrypt should run.
 * 2. Routing to the correct decrypt strategy by encrypted type.
 * 3. Delegating request construction and successful reentry details to helpers.
 */
@Service
public class MessageDecryptCoordinator {
    // REFACTOR STEP: MESSAGE_ROLE_RENAME

    private static final Logger log = LoggerFactory.getLogger(MessageDecryptCoordinator.class);

    private final DecryptRequestFactory decryptRequestFactory;
    private final DecryptPrerequisitePolicy decryptPrerequisitePolicy;
    private final DecryptGatewayClient decryptGatewayClient;
    private final DecryptResultApplier decryptResultApplier;
    private final DecryptReentryHandler decryptReentryHandler;

    /**
     * Creates the decrypt coordinator.
     */
    public MessageDecryptCoordinator(
            DecryptRequestFactory decryptRequestFactory,
            DecryptPrerequisitePolicy decryptPrerequisitePolicy,
            DecryptGatewayClient decryptGatewayClient,
            DecryptResultApplier decryptResultApplier,
            DecryptReentryHandler decryptReentryHandler
    ) {
        this.decryptRequestFactory = decryptRequestFactory;
        this.decryptPrerequisitePolicy = decryptPrerequisitePolicy;
        this.decryptGatewayClient = decryptGatewayClient;
        this.decryptResultApplier = decryptResultApplier;
        this.decryptReentryHandler = decryptReentryHandler;
    }

    /**
     * Attempts decrypt only when the current message is still encrypted.
     */
    public DecryptAttemptResult handleEncryptedMessageIfNeeded(MessageProcessingContext context) {
        if (!context.isEncrypted()) {
            return null;
        }

        SignalingMessage message = context.getMessage();
        String encType = context.getEncryptedType();
        UEContext ueContext = context.getUeContext();

        DecryptAttemptResult decryptResult = tryDecryptByType(message, encType, ueContext);
        context.setDecryptResult(decryptResult);

        if (decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
            log.info("Decrypt waiting: ueId={}, reason={}, msgId={}, encType={}",
                    message.getUeId(), decryptResult.getReason(), message.getMsgId(), encType);
        } else if (decryptResult.getStatus() == DecryptAttemptResult.Status.FAILED) {
            log.warn("Decrypt failed: ueId={}, msgId={}, encType={}, err={}",
                    message.getUeId(), message.getMsgId(), encType, decryptResult.getError());
        }

        return decryptResult;
    }

    /**
     * Handles decrypt success by reentering the reparsed decrypted content.
     */
    public boolean handleDecryptSuccess(MessageProcessingContext context) {
        SignalingMessage message = context.getMessage();
        String encType = context.getEncryptedType();

        try {
            decryptReentryHandler.reenterDecryptedMessage(message, encType);
            return true;
        } catch (Exception e) {
            log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    message.getUeId(), message.getMsgId(), encType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Routes decrypt by normalized encrypted type and current available keys.
     */
    public DecryptAttemptResult tryDecryptByType(SignalingMessage message, String encType, UEContext context) {
        String normalizedEncType = decryptRequestFactory.normalizeEncType(encType);

        if ("NONE".equals(normalizedEncType)) {
            return DecryptAttemptResult.skip();
        }

        DecryptAttemptResult depthCheck =
                decryptPrerequisitePolicy.validateDepth(message, normalizedEncType);
        if (depthCheck != null) {
            return depthCheck;
        }

        if ("NAS".equals(normalizedEncType)) {
            return decryptNasLayers(message, context);
        }

        if ("PDCP".equals(normalizedEncType)) {
            return decryptAs(message, context);
        }

        if ("NAS+PDCP".equals(normalizedEncType)) {
            DecryptAttemptResult nasResult = decryptNasLayers(message, context);

            if (nasResult.getStatus() == DecryptAttemptResult.Status.OK
                    || nasResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
                return nasResult;
            }

            return decryptAs(message, context);
        }

        return DecryptAttemptResult.skip();
    }

    /**
     * Attempts NAS decrypt against the first still-encrypted NAS layer that has decryptable data.
     */
    private DecryptAttemptResult decryptNasLayers(SignalingMessage message, UEContext context) {
        if (message.getNasList() == null || message.getNasList().isEmpty()) {
            return DecryptAttemptResult.skip();
        }

        for (int i = 0; i < message.getNasList().size(); i++) {
            NasInfo nas = message.getNasList().get(i);
            if (nas == null || !nas.isEncrypted()) {
                continue;
            }

            DecryptAttemptResult precheck = decryptPrerequisitePolicy.validateNasPrerequisites(context, nas);
            if (precheck != null) {
                return precheck;
            }

            DecryptRequest request = decryptRequestFactory.buildNasRequest(message, context, nas);
            DecryptGatewayResult response = decryptGatewayClient.decrypt(request, "NAS");

            if (response == null) {
                return DecryptAttemptResult.failed("NAS decrypt failed");
            }

            if (response.isSuccess()) {
                decryptResultApplier.applyNasResult(message, response, i, nas);
                return DecryptAttemptResult.ok();
            }

            return DecryptAttemptResult.failed("NAS decrypt failed");
        }

        return DecryptAttemptResult.skip();
    }

    /**
     * Attempts AS/PDCP decrypt when PDCP signalling data is present.
     */
    private DecryptAttemptResult decryptAs(SignalingMessage message, UEContext context) {
        PdcpInfo pdcp = message.getPdcpInfo();
        if (pdcp == null || !pdcp.isPdcpencrypted()) {
            return DecryptAttemptResult.skip();
        }

        DecryptAttemptResult precheck = decryptPrerequisitePolicy.validateAsPrerequisites(context, pdcp);
        if (precheck != null) {
            return precheck;
        }

        DecryptRequest request = decryptRequestFactory.buildAsRequest(message, context, pdcp);
        DecryptGatewayResult response = decryptGatewayClient.decrypt(request, "AS");

        if (response == null) {
            return DecryptAttemptResult.failed("AS decrypt failed");
        }

        if (response.isSuccess()) {
            decryptResultApplier.applyPdcpResult(message, response);
            return DecryptAttemptResult.ok();
        }

        return DecryptAttemptResult.failed("AS decrypt failed");
    }
}
