/**
 * 流程处理子域。
 *
 * 当前定位：
 * 1. 这里承载“流程识别、流程状态、流程阶段执行、流程评分与规则定义”相关能力
 * 2. 根包尽量只保留子域入口语义，具体职责下沉到更清楚的子包
 * 3. 当前子包划分为：
 *    - recognize：流程识别入口与识别编排
 *    - score：流程评分服务
 *    - stage：主链中的流程阶段执行与决策落地
 *    - state：流程状态生命周期与存储
 *    - flow：运行时匹配、关闭策略、handler 注册与 handler 接口
 *    - ruledef：静态规则定义
 *
 * 设计意图：
 * - 对当前单体来说，让流程子域更易读、更易维护
 * - 对未来演进来说，为流程识别器、流程状态服务、流程规则服务的独立化预留清晰边界
 */
// REFACTOR STEP: PROCESSING_ROOT_PACKAGE_INFO
package com.example.procedure.processing.procedure;
