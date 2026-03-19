package com.example.procedure.application.pcap;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.application.message.SignalingMessagePipeline;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.support.logging.StageLogRefs;
import com.example.procedure.wireshark.TsharkRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/**
 * pcap 批处理应用编排器。
 *
 * 当前定位：
 * - 它是 pcap 批处理在 application 层的主入口
 * - 它负责“文件输入 -> tshark JSON 流 -> SignalingMessage”
 * - 它不负责单条消息进入主链后的绑定与处理细节
 *
 * 当前主流程：
 * 1. 调用 tshark 将 pcap 解码为 JSON 流
 * 2. 从 JSON 流中解析出 SignalingMessage
 * 3. 把每条消息交给 SignalingMessagePipeline 继续处理
 *
 * 当前阶段日志：
 * - 已补齐批处理入口与阶段日志
 * - 引用信息统一复用 StageLogRefs
 *
 * 第 26 小步的重点：
 * - 把 application 层重复的阶段异常构造逻辑统一收口到 ApplicationStageErrors
 */
@Service
public class PcapBatchOrchestrator implements PcapBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(PcapBatchOrchestrator.class);

    private final TsharkRunner tsharkRunner;
    private final SignalingMessagePipeline signalingMessagePipeline;

    public PcapBatchOrchestrator(
            TsharkRunner tsharkRunner,
            SignalingMessagePipeline signalingMessagePipeline
    ) {
        this.tsharkRunner = tsharkRunner;
        this.signalingMessagePipeline = signalingMessagePipeline;
    }

    @Override
    public void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        logBatchEntry(pcap, wanted, enabledRaw);

        parsePcapToMessages(
                pcap,
                wanted,
                enabledRaw,
                this::forwardToMessagePipeline
        );

        logBatchExit(pcap);
    }

    private void parsePcapToMessages(
            Path pcap,
            Set<String> wanted,
            Set<String> enabledRaw,
            Consumer<SignalingMessage> sink
    ) throws Exception {
        log.debug("Pcap stage[decode-stream] enter: pcap={}, wanted={}, enabledRaw={}",
                StageLogRefs.pcap(pcap),
                StageLogRefs.size(wanted),
                StageLogRefs.size(enabledRaw));

        ChainsInspectConsumer consumer = new ChainsInspectConsumer(sink::accept);

        try {
            tsharkRunner.decodeToJsonStream(pcap, in -> {
                try {
                    log.debug("Pcap stage[json-parse] enter: pcap={}", StageLogRefs.pcap(pcap));

                    LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);

                    log.debug("Pcap stage[json-parse] exit: pcap={}", StageLogRefs.pcap(pcap));
                } catch (IOException e) {
                    throw ApplicationStageErrors.forPcap(
                            "pcap-parse",
                            pcap,
                            "Failed to parse tshark JSON stream into SignalingMessage objects",
                            e
                    );
                }
            });

            log.debug("Pcap stage[decode-stream] exit: pcap={}", StageLogRefs.pcap(pcap));
        } catch (ApplicationStageException e) {
            throw e;
        } catch (Exception e) {
            throw ApplicationStageErrors.forPcap(
                    "pcap-decode",
                    pcap,
                    "Failed while decoding pcap through tshark pipeline",
                    e
            );
        }
    }

    private void forwardToMessagePipeline(SignalingMessage msg) {
        log.debug("Pcap stage[forward-message] enter: {}",
                StageLogRefs.message(msg));

        try {
            signalingMessagePipeline.process(msg);

            log.debug("Pcap stage[forward-message] exit: {}",
                    StageLogRefs.message(msg));
        } catch (ApplicationStageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApplicationStageErrors.forMessage(
                    "message-pipeline",
                    msg,
                    "Failed while forwarding signaling message into message pipeline",
                    e
            );
        }
    }

    private void logBatchEntry(Path pcap, Set<String> wanted, Set<String> enabledRaw) {
        log.info("Pcap batch enter: pcap={}, wanted={}, enabledRaw={}",
                StageLogRefs.pcap(pcap),
                StageLogRefs.size(wanted),
                StageLogRefs.size(enabledRaw));
    }

    private void logBatchExit(Path pcap) {
        log.info("Pcap batch exit: pcap={}", StageLogRefs.pcap(pcap));
    }
}
