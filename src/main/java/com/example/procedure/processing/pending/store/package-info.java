/**
 * pending decrypt 存储子包。
 *
 * 当前定位：
 * 1. 放置等待记录、仓储边界与当前进程内实现
 * 2. 让队列编排和底层存储责任分离
 * 3. 为后续替换 Redis / MQ / DB waiting store 提供稳定位置
 */
// REFACTOR STEP: PENDING_SUBPACKAGE_REORG
package com.example.procedure.processing.pending.store;
