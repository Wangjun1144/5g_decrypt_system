/**
 * 消息主处理链中的 pending 解密重试子包。
 *
 * 当前定位：
 * 1. 这里放 waiting/pending 消息在主链内的重试与重入能力
 * 2. message 根包保留主链编排器，retry 子包承接“条件成熟后再试一次”的处理职责
 * 3. 未来如果要把 pending retry 拆成独立 worker、任务调度器或远程服务，这里是自然边界
 */
// REFACTOR STEP: MESSAGE_RETRY_SUBPACKAGE_REORG
package com.example.procedure.processing.message.retry;
