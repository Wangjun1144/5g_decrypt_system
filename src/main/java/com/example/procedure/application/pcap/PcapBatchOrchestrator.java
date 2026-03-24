package com.example.procedure.application.pcap;

import com.example.procedure.application.ApplicationStageErrors;
import com.example.procedure.application.ApplicationStageException;
import com.example.procedure.application.message.SignalingMessagePipeline;
import com.example.procedure.application.message.SignalingMessagePipelineRequest;
import com.example.procedure.decodebridge.PcapDecodeRequest;
import com.example.procedure.decodebridge.PcapParseBridgeService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.StageLogRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * pcap 批处理应用编排器。
 *
 * 当前定位：
 * 1. 它是 application 层的 pcap 批处理主入口
 * 2. 它负责编排“pcap -> message pipeline”的整体流程
 * 3. 它不再直接依赖 tshark 底层实现，而是通过 decodebridge 边界访问解码能力
 */
@Service
public class PcapBatchOrchestrator implements PcapBatchProcessor {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(PcapBatchOrchestrator.class);

    /**
     * pcap 解析 bridge 服务。
     */
    private final PcapParseBridgeService pcapParseBridgeService;

    /**
     * 单条消息 pipeline。
     */
    private final SignalingMessagePipeline signalingMessagePipeline;

    /**
     * 构造 pcap 批处理编排器。
     *
     * @param pcapParseBridgeService pcap 解析 bridge
     * @param signalingMessagePipeline 单条消息 pipeline
     */
    public PcapBatchOrchestrator(
            PcapParseBridgeService pcapParseBridgeService,
            SignalingMessagePipeline signalingMessagePipeline
    ) {
        this.pcapParseBridgeService = pcapParseBridgeService;
        this.signalingMessagePipeline = signalingMessagePipeline;
    }

    /**
     * 正式入口：处理一个 pcap 批处理请求。
     *
     * 当前执行过程：
     * 1. 记录批处理入口日志
     * 2. 构造本次批处理的 correlationId
     * 3. 通过 decodebridge 把 pcap 解析成消息
     * 4. 把每条消息送入 signaling message pipeline
     *
     * @param request pcap 批处理请求
     * @throws Exception 批处理失败时抛出异常
     */
    @Override
    public void process(PcapBatchProcessRequest request) throws Exception {
        Path pcap = request.getPcap();
        Set<String> wanted = request.getWanted();
        Set<String> enabledRaw = request.getEnabledRaw();

        logBatchEntry(pcap, wanted, enabledRaw);

        String batchCorrelationId = buildBatchCorrelationId(pcap);

        parsePcapToMessages(
                pcap,
                wanted,
                enabledRaw,
                msg -> forwardToMessagePipeline(msg, pcap, batchCorrelationId)
        );

        logBatchExit(pcap);
    }

    /**
     * 把一个 pcap 解析成消息流。
     *
     * 当前这里不再直接操作 tshark，
     * 而是统一通过 PcapParseBridgeService 进入 decodebridge 边界。
     *
     * @param pcap 当前 pcap 文件
     * @param wanted 需要保留的协议层
     * @param enabledRaw 需要启用 raw 输出的层
     * @param sink 下游消息消费者
     * @throws Exception 解析失败时抛出异常
     */
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

        try {
            pcapParseBridgeService.parse(
                    PcapDecodeRequest.of(pcap, wanted, enabledRaw, sink)
            );

            log.debug("Pcap stage[decode-stream] exit: pcap={}", StageLogRefs.pcap(pcap));
        } catch (ApplicationStageException e) {
            throw e;
        } catch (Exception e) {
            throw ApplicationStageErrors.forPcap(
                    "pcap-decode",
                    pcap,
                    "Failed while decoding pcap through decode bridge",
                    e
            );
        }
    }

    /**
     * 把一条解码后的消息送入单条消息 pipeline。
     *
     * @param msg 当前消息
     * @param pcap 当前来源 pcap
     * @param batchCorrelationId 当前批处理关联 ID
     */
    private void forwardToMessagePipeline(
            SignalingMessage msg,
            Path pcap,
            String batchCorrelationId
    ) {
        log.debug("Pcap stage[forward-message] enter: {}, correlationId={}",
                StageLogRefs.message(msg),
                batchCorrelationId);

        try {
            signalingMessagePipeline.process(
                    SignalingMessagePipelineRequest.fromPcap(
                            msg,
                            pcap == null ? null : pcap.toString(),
                            batchCorrelationId
                    )
            );

            log.debug("Pcap stage[forward-message] exit: {}, correlationId={}",
                    StageLogRefs.message(msg),
                    batchCorrelationId);
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

    /**
     * 构造一次 pcap 批处理的关联 ID。
     *
     * @param pcap 当前 pcap 文件
     * @return 本次批处理的 correlationId
     */
    private String buildBatchCorrelationId(Path pcap) {
        String name = pcap == null ? "pcap" : pcap.getFileName().toString();
        return "pcap-" + name + "-" + UUID.randomUUID();
    }

    /**
     * 记录批处理入口日志。
     *
     * @param pcap 当前 pcap 文件
     * @param wanted wanted 集合
     * @param enabledRaw enabledRaw 集合
     */
    private void logBatchEntry(Path pcap, Set<String> wanted, Set<String> enabledRaw) {
        log.info("Pcap batch enter: pcap={}, wanted={}, enabledRaw={}",
                StageLogRefs.pcap(pcap),
                StageLogRefs.size(wanted),
                StageLogRefs.size(enabledRaw));
    }

    /**
     * 记录批处理出口日志。
     *
     * @param pcap 当前 pcap 文件
     */
    private void logBatchExit(Path pcap) {
        log.info("Pcap batch exit: pcap={}", StageLogRefs.pcap(pcap));
    }
}
