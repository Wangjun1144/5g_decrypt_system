/**
 * 流程静态规则定义包。
 *
 * 当前定位：
 * 1. 放置流程识别与评分依赖的静态定义，如 phases、key bits、phase model
 * 2. 这些对象服务于 processing.procedure 子域，但本身不承担运行期编排职责
 * 3. 这样可以把运行期 flow 组件与静态规则定义分开收纳
 */
// REFACTOR STEP: PROCEDURE_STATIC_RULEDEF_REHOME
package com.example.procedure.processing.procedure.ruledef;
