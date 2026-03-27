/**
 * 流程子域中的评分子包。
 *
 * 当前定位：
 * 1. 这里放流程匹配评分服务
 * 2. 与 recognize、flow、state 解耦，便于后续独立演进评分策略
 * 3. 未来如果把评分规则做成可配置组件或远程决策能力，这里是自然边界
 */
// REFACTOR STEP: PROCEDURE_RECOGNIZE_SCORE_SUBPACKAGE_REORG
package com.example.procedure.processing.procedure.score;
