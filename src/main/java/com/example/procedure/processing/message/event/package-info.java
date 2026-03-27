/**
 * message 阶段事件子包。
 *
 * 当前定位：
 * 1. 放置主处理链阶段事件模型与发布边界
 * 2. 让 observability / audit 语义与主处理编排分层
 * 3. 为后续 outbox / MQ / tracing 演进预留独立位置
 */
// REFACTOR STEP: MESSAGE_SUBPACKAGE_REORG
package com.example.procedure.processing.message.event;
