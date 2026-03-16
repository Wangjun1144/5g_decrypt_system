package com.example.procedure.service;

import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.rule.UeIdBinder;
import com.example.procedure.streaming.layers.ChainsInspectConsumer;
import com.example.procedure.streaming.layers.LayersSelectiveParser;
import com.example.procedure.util.SignalingMessagePrinter;
import com.example.procedure.wireshark.TsharkRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 默认 pcap 批处理服务
 *
 * 职责：
 * 1. 调用 tsharkRunner 将 pcap 解成 JSON 流
 * 2. 调用 LayersSelectiveParser 只提取关心的 layer
 * 3. 将 chain 转成 SignalingMessage
 * 4. 统一走 UE 绑定 + 消息处理服务
 *
 * 这样一来，测试类就不需要再自己编排整个入口流程。
 */
@Service
public class DefaultPcapBatchProcessingService implements PcapBatchProcessingService {

    private final TsharkRunner tsharkRunner;
    private final UeIdBinder ueIdBinder;
    private final MsgProcessing_Service messageProcessingService;

    public DefaultPcapBatchProcessingService(
            TsharkRunner tsharkRunner,
            UeIdBinder ueIdBinder,
            MsgProcessing_Service messageProcessingService
    ) {
        this.tsharkRunner = tsharkRunner;
        this.ueIdBinder = ueIdBinder;
        this.messageProcessingService = messageProcessingService;
    }

    @Override
    public void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        // consumer 负责把 parser 产出的 chain 转成 SignalingMessage，
        // 然后交给 processMessage(...) 继续处理
        ChainsInspectConsumer consumer = new ChainsInspectConsumer(this::processMessage);

        tsharkRunner.decodeToJsonStream(pcap, in -> {
            try {
                LayersSelectiveParser.parsePackets(in, wanted, enabledRaw, consumer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 单条 SignalingMessage 的统一处理入口
     */
    private void processMessage(SignalingMessage msg) {
        ueIdBinder.handle(msg, boundMsg -> {
            MessageProcessingResult result = messageProcessingService.process(boundMsg);

            // 目前先保留原有日志输出行为，不改变功能
            SignalingMessagePrinter.printAndWriteToFile(
                    boundMsg,
                    Paths.get("logs/signaling_dump.log"),
                    true
            );
        });
    }
}