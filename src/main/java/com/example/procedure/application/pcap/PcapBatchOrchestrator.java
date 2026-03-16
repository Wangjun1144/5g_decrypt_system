package com.example.procedure.application.pcap;

import com.example.procedure.domain.binding.UeBindingService;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.processing.message.MessageProcessor;
import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.support.logging.SignalingDumpWriter;
import com.example.procedure.wireshark.TsharkRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * pcap 批处理总编排器。
 *
 * 阶段 1：
 * - 只做命名规范化和职责收口
 * - 不改变现有链路行为
 *
 * 阶段 2：
 * - 将这里演进为显式 pipeline orchestrator
 */
@Service
public class PcapBatchOrchestrator implements PcapBatchProcessor {

    private final TsharkRunner tsharkRunner;
    private final UeBindingService ueBindingService;
    private final MessageProcessor messageProcessor;

    public PcapBatchOrchestrator(
            TsharkRunner tsharkRunner,
            UeBindingService ueBindingService,
            MessageProcessor messageProcessor
    ) {
        this.tsharkRunner = tsharkRunner;
        this.ueBindingService = ueBindingService;
        this.messageProcessor = messageProcessor;
    }

    @Override
    public void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        ChainsInspectConsumer consumer = new ChainsInspectConsumer(this::processMessage);

        tsharkRunner.decodeToJsonStream(pcap, in -> {
            try {
                LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void processMessage(SignalingMessage msg) {
        ueBindingService.handle(msg, boundMsg -> {
            messageProcessor.process(boundMsg);

            // 保留原行为：仍然输出日志文件
            SignalingDumpWriter.write(boundMsg, Paths.get("logs/signaling_dump.log"), true);
        });
    }
}