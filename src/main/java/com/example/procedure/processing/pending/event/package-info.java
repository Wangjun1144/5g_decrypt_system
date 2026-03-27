/**
 * pending decrypt 事件子包。
 *
 * 当前定位：
 * 1. 放置等待状态事件模型与发布边界
 * 2. 让 pending 生命周期观测与队列/存储实现解耦
 * 3. 为后续事件总线或 outbox 演进留出独立位置
 */
// REFACTOR STEP: PENDING_SUBPACKAGE_REORG
package com.example.procedure.processing.pending.event;
