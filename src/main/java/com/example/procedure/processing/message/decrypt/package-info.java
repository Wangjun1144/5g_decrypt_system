/**
 * 消息处理链中的解密阶段子包。
 *
 * 当前定位：
 * 1. 这里放消息主链中的“是否解密、如何解密、解密后如何回流”的阶段能力
 * 2. message 根包保留主链编排器，decrypt 子包承接具体解密阶段职责
 * 3. 未来如果要把解密阶段抽成独立模块、worker 或远程服务，这里是自然边界
 */
// REFACTOR STEP: MESSAGE_DECRYPT_SUBPACKAGE_REORG
package com.example.procedure.processing.message.decrypt;
