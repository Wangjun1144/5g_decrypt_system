package com.example.procedure.processing.message.decrypt;

import com.example.procedure.infrastructure.decode.bridge.reentry.DecryptResultReentryService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.support.logging.SignalingMessagePrinter;
import com.example.procedure.processing.message.decrypt.support.ReentryNodeMergeSupport;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * Handles decrypt-success reentry and merge-back into the original signaling message.
 *
 * This isolates the tree merge and post-reentry bookkeeping from the decrypt coordinator.
 */
@Component
public class DecryptReentryHandler {

    private final DecryptResultReentryService decryptResultReentryService;
    private final DecryptRequestFactory decryptRequestFactory;

    /**
     * Creates the reentry handler.
     */
    public DecryptReentryHandler(
            DecryptResultReentryService decryptResultReentryService,
            DecryptRequestFactory decryptRequestFactory
    ) {
        this.decryptResultReentryService = decryptResultReentryService;
        this.decryptRequestFactory = decryptRequestFactory;
    }

    /**
     * Reenters the decrypted message, merges reparsed content, and updates decrypt bookkeeping.
     */
    public void reenterDecryptedMessage(SignalingMessage message, String decryptedLayer) throws Exception {
        decryptResultReentryService.reenter(message, reparsedMsg -> {
            attachReparsedSourceNodeId(message, reparsedMsg);

            String normalizedLayer = decryptRequestFactory.normalizeEncType(decryptedLayer);
            if ("NAS".equals(normalizedLayer)) {
                mergeNasDecodedContent(message, reparsedMsg);
            } else if ("PDCP".equals(normalizedLayer)) {
                mergePdcpDecodedContent(message, reparsedMsg);
            } else if ("NAS+PDCP".equals(normalizedLayer)) {
                mergeNasDecodedContent(message, reparsedMsg);
                mergePdcpDecodedContent(message, reparsedMsg);
            }

            message.setEncrypted(
                    reparsedMsg.getEncrypted() != null ? reparsedMsg.getEncrypted() : false
            );
            message.setEncryptedType(
                    !isBlank(reparsedMsg.getEncryptedType())
                            ? decryptRequestFactory.normalizeEncType(reparsedMsg.getEncryptedType())
                            : "NONE"
            );

            if (isBlank(message.getDecryptPlainHex()) && !isBlank(reparsedMsg.getDecryptPlainHex())) {
                message.setDecryptPlainHex(reparsedMsg.getDecryptPlainHex());
            }
            if (isBlank(message.getDecryptMacHex()) && !isBlank(reparsedMsg.getDecryptMacHex())) {
                message.setDecryptMacHex(reparsedMsg.getDecryptMacHex());
            }

            message.setDecrypted(true);
            message.setDecryptDepth(safeDecryptDepth(message) + 1);
            message.setDecryptPath(appendDecryptPath(message.getDecryptPath(), normalizedLayer));

            if (message.getEncrypted() == null) {
                message.setEncrypted(false);
            }
            if (isBlank(message.getEncryptedType())) {
                message.setEncryptedType("NONE");
            }

            // Keep a dedicated reentry dump so decrypted merge behavior remains easy to inspect.
            SignalingMessagePrinter.printAndWriteToFile(
                    message,
                    Paths.get("logs/signaling_reentry_dump.log"),
                    true
            );
        });
    }

    private void attachReparsedSourceNodeId(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }
        if (isBlank(originalMsg.getDecryptTargetNodeId())) {
            return;
        }

        reparsedMsg.setReentrySourceNodeId(originalMsg.getDecryptTargetNodeId());

        if (reparsedMsg.getNasList() != null && !reparsedMsg.getNasList().isEmpty()) {
            NasInfo nas = reparsedMsg.getNasList().get(0);
            if (nas != null && isBlank(nas.getSourceNodeId())) {
                nas.setSourceNodeId(originalMsg.getDecryptTargetNodeId());
            }
        }

        if (reparsedMsg.getRrcInfo() != null && isBlank(reparsedMsg.getRrcInfo().getSourceNodeId())) {
            reparsedMsg.getRrcInfo().setSourceNodeId(originalMsg.getDecryptTargetNodeId());
        }

        if (reparsedMsg.getPdcpInfo() != null && isBlank(reparsedMsg.getPdcpInfo().getSourceNodeId())) {
            reparsedMsg.getPdcpInfo().setSourceNodeId(originalMsg.getDecryptTargetNodeId());
        }
    }

    private void mergeNasDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }
        if (originalMsg.getNasList() == null || originalMsg.getNasList().isEmpty()) {
            return;
        }
        if (reparsedMsg.getNasList() == null || reparsedMsg.getNasList().isEmpty()) {
            return;
        }

        String sourceNodeId = reparsedMsg.getReentrySourceNodeId();
        if (isBlank(sourceNodeId) && !reparsedMsg.getNasList().isEmpty()) {
            NasInfo reparsedRootNas = reparsedMsg.getNasList().get(0);
            if (reparsedRootNas != null) {
                sourceNodeId = reparsedRootNas.getSourceNodeId();
            }
        }
        if (isBlank(sourceNodeId)) {
            sourceNodeId = originalMsg.getDecryptTargetNodeId();
        }
        if (isBlank(sourceNodeId)) {
            return;
        }

        NasInfo targetNas = ReentryNodeMergeSupport.findNasByNodeId(originalMsg, sourceNodeId);
        if (targetNas == null) {
            return;
        }

        NasInfo reparsedRootNas = reparsedMsg.getNasList().get(0);
        if (reparsedRootNas == null) {
            return;
        }

        ReentryNodeMergeSupport.mergeNasPayloadFields(
                targetNas,
                reparsedRootNas,
                originalMsg.getDecryptPlainHex()
        );

        ReentryNodeMergeSupport.graftReparsedTreeIntoOriginal(
                originalMsg,
                reparsedMsg,
                sourceNodeId,
                true
        );

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }
        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }
    }

    private void mergePdcpDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }

        if (reparsedMsg.getRrcInfo() != null) {
            originalMsg.setRrcInfo(reparsedMsg.getRrcInfo());
        }

        if (reparsedMsg.getNasList() != null && !reparsedMsg.getNasList().isEmpty()) {
            originalMsg.setNasList(reparsedMsg.getNasList());
        }

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }

        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }

        if (reparsedMsg.getMessageTree() != null) {
            originalMsg.setMessageTree(reparsedMsg.getMessageTree());
        }
    }

    private int safeDecryptDepth(SignalingMessage msg) {
        if (msg == null || msg.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(msg.getDecryptDepth(), 0);
    }

    private String appendDecryptPath(String oldPath, String layer) {
        if (isBlank(layer) || "NONE".equals(layer)) {
            return oldPath;
        }
        if (isBlank(oldPath)) {
            return layer;
        }
        return oldPath + "->" + layer;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
