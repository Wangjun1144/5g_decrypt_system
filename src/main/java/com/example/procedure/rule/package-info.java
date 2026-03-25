/**
 * 静态规则定义包。
 *
 * 当前定位：
 * 1. 这里主要放 phases、key bits、消息分类等静态规则
 * 2. 运行期编排、流程关闭策略、流程匹配执行不应继续放在这里
 * 3. 运行期逻辑应逐步下沉到 processing.procedure 相关包
 */
// REFACTOR STEP: RULE_FLOW_BOUNDARY
package com.example.procedure.rule;
