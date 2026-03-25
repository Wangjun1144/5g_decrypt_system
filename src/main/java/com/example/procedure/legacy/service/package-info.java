/**
 * legacy 兼容 facade 包。
 *
 * 当前定位：
 * 1. 这里放仍需保留的旧能力兼容门面
 * 2. 新代码不应继续直接依赖这些 facade
 * 3. 旧的 com.example.procedure.service 包会逐步退成超薄包装壳
 *
 * 当前整理原则：
 * 1. 正式实现放在 application / processing / context / infrastructure 等明确包中
 * 2. legacy facade 只负责兼容旧命名、旧调用方式
 * 3. 后续删除旧 service 壳时，这里是自然承接点
 */
// REFACTOR STEP: LEGACY_SERVICE_FACADE_REORG
package com.example.procedure.legacy.service;
