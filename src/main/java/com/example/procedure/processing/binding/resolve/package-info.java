/**
 * binding 解析子包。
 *
 * 当前定位：
 * 1. 放置身份绑定解析与 pending flush 协调逻辑
 * 2. 让解析执行职责从 stage 入口中下沉
 * 3. 为后续把绑定决策核心独立抽离预留位置
 */
// REFACTOR STEP: BINDING_SUBPACKAGE_REORG
package com.example.procedure.processing.binding.resolve;
