/**
 * 消息主处理链中的流程阶段子包。
 *
 * 当前定位：
 * 1. 这里放流程处理阶段的统一入口、默认编排实现和决策落地组件
 * 2. processing.procedure 根包保留流程子域入口服务，stage 子包承接主链中的阶段执行职责
 * 3. 未来如果要把流程识别与流程落地拆成更清晰的应用层/worker，这里是自然边界
 */
// REFACTOR STEP: PROCEDURE_STAGE_SUBPACKAGE_REORG
package com.example.procedure.processing.procedure.stage;
