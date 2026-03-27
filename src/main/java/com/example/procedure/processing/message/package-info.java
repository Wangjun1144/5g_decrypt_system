/**
 * 消息主处理子域。
 *
 * 当前定位：
 * 1. 这里承载单条信令消息的主处理链，是应用主流程进入各个处理阶段的核心子域
 * 2. 根包只保留主链编排器与少量核心协调组件，细分职责下沉到子包
 * 3. 当前子包划分为：
 *    - classify：消息分类阶段
 *    - decrypt：解密阶段与解密回流协调
 *    - retry：pending 解密重试与重入
 *    - runtime：主链运行时上下文与请求对象
 *    - event：主链阶段事件
 *    - result：结果装配与摘要输出
 *
 * 设计意图：
 * - 先把当前单体做成阶段清晰、协作清晰的高质量主链
 * - 未来如果把解密、重试、分类拆成独立模块或服务，这里已经具备自然边界
 */
// REFACTOR STEP: PROCESSING_ROOT_PACKAGE_INFO
package com.example.procedure.processing.message;
