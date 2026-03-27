/**
 * pending decrypt 队列子包。
 *
 * 当前定位：
 * 1. 放置等待项模型、队列边界与默认队列实现
 * 2. 保持上层只依赖“等待队列”语义，而不关心具体存储结构
 * 3. 为后续将 retry queue 独立成模块或服务预留入口
 */
// REFACTOR STEP: PENDING_SUBPACKAGE_REORG
package com.example.procedure.processing.pending.queue;
