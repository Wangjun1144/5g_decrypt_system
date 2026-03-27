/**
 * 流程子域中的识别入口子包。
 *
 * 当前定位：
 * 1. 这里放流程识别入口服务与识别编排逻辑
 * 2. processing.procedure 根包不再承担识别实现细节，recognize 子包承接识别职责
 * 3. 未来如果把识别器独立成模块或服务，这里是自然入口边界
 */
// REFACTOR STEP: PROCEDURE_RECOGNIZE_SCORE_SUBPACKAGE_REORG
package com.example.procedure.processing.procedure.recognize;
