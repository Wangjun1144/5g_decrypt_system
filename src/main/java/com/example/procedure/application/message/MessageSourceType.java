package com.example.procedure.application.message;

/**
 * 单条消息进入主链时的来源类型。
 *
 * 当前阶段先收口最常见的来源：
 * - PCAP：来自 pcap/pcapng 批处理
 * - DIRECT：来自直接调用、测试或手工喂入
 * - REENTRY：来自解密回流、重处理等内部再进入
 *
 * 后续可继续扩展：
 * - STREAM
 * - API
 * - MQ
 */
public enum MessageSourceType {
    PCAP,
    DIRECT,
    REENTRY
}
