package com.example.procedure.infrastructure.parser.streaming.layers;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.infrastructure.parser.streaming.parser.MessageTreeBuilder;
import com.example.procedure.infrastructure.parser.streaming.parser.StreamingChainParseResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Emits normalized signaling messages from parsed streaming chains.
 *
 * Each chain becomes one message with a stable message id and payload node ids
 * so it can re-enter the normal processing pipeline.
 */
public final class StreamingMessageEmitter implements Consumer<List<StreamingChainParseResult>> {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final Consumer<SignalingMessage> onMessage;

    public StreamingMessageEmitter(Consumer<SignalingMessage> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void accept(List<StreamingChainParseResult> chains) {
        if (chains == null || chains.isEmpty()) {
            return;
        }

        for (StreamingChainParseResult chain : chains) {
            long id = SEQ.incrementAndGet();
            String msgId = "MSG-" + id;
            SignalingMessage msg = buildMessage(chain, msgId);
            if (msg != null) {
                msg.setMsgId(msgId);
                msg.setMessageTree(MessageTreeBuilder.fromChainIndex(msg.getMsgId(), chain.getIndex()));
                onMessage.accept(msg);
            }
        }
    }

    /**
     * Build one signaling message from one parsed chain.
     *
     * @param chain parsed streaming chain
     * @param msgId generated message id
     * @return assembled signaling message
     */
    private SignalingMessage buildMessage(StreamingChainParseResult chain, String msgId) {
        if (chain == null) {
            return null;
        }

        SignalingMessage msg = new SignalingMessage();
        msg.setFrameNo(chain.getFrameNo());
        msg.setTimestamp(chain.getTimestampMs());
        msg.setIface(chain.getIface());
        msg.setDirection(chain.getDirection());
        msg.setMsgType(StreamingChainParseResult.normalizedMsgType(chain.getMsgCode()));
        msg.setUeId(chain.getUeId());
        msg.setEncrypted(chain.getEncrypted());
        msg.setEncryptedType(chain.getEncryptedType());
        msg.setMacInfo(first(chain.getMacList()));
        msg.setPdcpInfo(first(chain.getPdcpList()));
        msg.setRrcInfo(first(chain.getRrcList()));
        msg.setNgapInfoList(chain.getNgapList() == null ? List.of() : chain.getNgapList());
        msg.setNuarInfo(chain.getNuarInfo());
        msg.setNasList(chain.getNasList() == null ? List.of() : chain.getNasList());
        fillPayloadNodeIds(msg, msgId);
        return msg;
    }

    private void fillPayloadNodeIds(SignalingMessage msg, String msgId) {
        if (msg == null || msgId == null) {
            return;
        }

        if (msg.getMacInfo() != null) {
            msg.getMacInfo().setNodeId(buildNodeId(msgId, msg.getMacInfo().getSequence()));
        }
        if (msg.getPdcpInfo() != null) {
            msg.getPdcpInfo().setNodeId(buildNodeId(msgId, msg.getPdcpInfo().getSequence()));
        }
        if (msg.getRrcInfo() != null) {
            msg.getRrcInfo().setNodeId(buildNodeId(msgId, msg.getRrcInfo().getSequence()));
        }
        if (msg.getNuarInfo() != null) {
            msg.getNuarInfo().setNodeId(buildNodeId(msgId, msg.getNuarInfo().getSequence()));
        }

        if (msg.getNasList() != null) {
            for (NasInfo nas : msg.getNasList()) {
                if (nas != null) {
                    nas.setNodeId(buildNodeId(msgId, nas.getSequence()));
                }
            }
        }

        if (msg.getNgapInfoList() != null) {
            for (NgapInfo ngap : msg.getNgapInfoList()) {
                if (ngap != null) {
                    ngap.setNodeId(buildNodeId(msgId, ngap.getSequence()));
                }
            }
        }
    }

    private static String buildNodeId(String messageId, int sequence) {
        return messageId + ":N" + sequence;
    }

    private static <T> T first(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }
}
