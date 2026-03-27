/**
 * 消息主处理链中的分类阶段子包。
 *
 * 当前定位：
 * 1. 这里放消息分类阶段的门面服务和静态分类规则组件
 * 2. message 根包保留主链编排器，classify 子包承接分类阶段职责
 * 3. 未来如果要把分类规则改造成可配置策略或独立决策模块，这里是自然边界
 */
// REFACTOR STEP: MESSAGE_CLASSIFY_SUBPACKAGE_REORG
package com.example.procedure.processing.message.classify;
