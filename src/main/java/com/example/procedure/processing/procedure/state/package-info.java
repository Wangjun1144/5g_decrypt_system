/**
 * 流程子域中的状态管理子包。
 *
 * 当前定位：
 * 1. 这里放流程生命周期相关的正式状态服务、状态存储服务和状态操作结果对象
 * 2. processing.procedure 根包保留流程识别入口，state 子包承接状态管理职责
 * 3. 未来如果要把流程状态存储拆到独立 repository、独立服务或事件驱动存储，这里是自然边界
 */
// REFACTOR STEP: PROCEDURE_STATE_SUBPACKAGE_REORG
package com.example.procedure.processing.procedure.state;
