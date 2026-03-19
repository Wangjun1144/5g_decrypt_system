package com.example.procedure.processing.message;

import com.example.procedure.decrypt.DecryptAttemptResult;
import org.springframework.stereotype.Service;

/**
 * 解密阶段的默认实现。
 *
 * 当前定位：
 * - 这是新的“解密阶段入口”
 * - 内部继续复用 DecryptCoordinator
 * - 不改动任何现有解密规则、算法映射、回流细节
 *
 * 为什么这一层值得加：
 * - 当前主链已经开始逐步阶段化
 * - 解密是未来最有可能独立演化为远程服务或异步处理环节的部分
 * - 先把入口边界稳定下来，后续替换底层实现时影响最小
 *
 * 当前阶段策略：
 * - 只做透明委托
 * - 不引入新行为
 * - 不修改主链控制权
 */
@Service
public class DefaultMessageDecryptStage implements MessageDecryptStage {

    private final DecryptCoordinator decryptCoordinator;

    public DefaultMessageDecryptStage(DecryptCoordinator decryptCoordinator) {
        this.decryptCoordinator = decryptCoordinator;
    }

    @Override
    public DecryptAttemptResult handleEncryptedMessageIfNeeded(MessageProcessingContext context) {
        return decryptCoordinator.handleEncryptedMessageIfNeeded(context);
    }

    @Override
    public boolean handleDecryptSuccess(MessageProcessingContext context) {
        return decryptCoordinator.handleDecryptSuccess(context);
    }
}
