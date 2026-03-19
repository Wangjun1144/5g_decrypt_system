package com.example.procedure.application;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.support.logging.StageLogRefs;

import java.nio.file.Path;

/**
 * application 层阶段异常构造工具。
 *
 * 设计目的：
 * 1. 统一 application 层的阶段异常构造逻辑
 * 2. 避免在多个 application 入口类中重复拼接 reference 和 message
 * 3. 让阶段异常的格式在 pcap 入口与单消息入口之间保持一致
 *
 * 当前支持的引用对象：
 * - pcap Path
 * - SignalingMessage
 *
 * 当前阶段保持简单：
 * - 不引入复杂异常工厂体系
 * - 只统一最常用的“stage + reference + message + cause”构造方式
 */
public final class ApplicationStageErrors {

    private ApplicationStageErrors() {
    }

    /**
     * 基于 pcap 文件构造阶段异常。
     */
    public static ApplicationStageException forPcap(
            String stage,
            Path pcap,
            String message,
            Throwable cause
    ) {
        String reference = pcap == null ? null : pcap.toString();
        return new ApplicationStageException(
                stage,
                reference,
                withReference(message, reference),
                cause
        );
    }

    /**
     * 基于单条消息构造阶段异常。
     */
    public static ApplicationStageException forMessage(
            String stage,
            SignalingMessage msg,
            String message,
            Throwable cause
    ) {
        String reference = StageLogRefs.message(msg);
        return new ApplicationStageException(
                stage,
                reference,
                withReference(message, reference),
                cause
        );
    }

    /**
     * 给异常消息补充统一 reference 文本。
     *
     * 统一格式：
     * 原始消息 + " [ref=...]"
     */
    public static String withReference(String message, String reference) {
        if (reference == null || reference.isBlank()) {
            return message;
        }
        return message + " [ref=" + reference + "]";
    }
}
