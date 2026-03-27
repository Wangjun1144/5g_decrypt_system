package com.example.procedure.processing.message.decrypt;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import org.springframework.stereotype.Service;

/**
 * 瑙ｅ瘑闃舵鐨勯粯璁ゅ疄鐜般€?
 *
 * 褰撳墠瀹氫綅锛?
 * - 杩欐槸鏂扮殑鈥滆В瀵嗛樁娈靛叆鍙ｂ€?
 * - 鍐呴儴缁х画澶嶇敤 MessageDecryptCoordinator
 * - 涓嶆敼鍔ㄤ换浣曠幇鏈夎В瀵嗚鍒欍€佺畻娉曟槧灏勩€佸洖娴佺粏鑺?
 *
 * 涓轰粈涔堣繖涓€灞傚€煎緱鍔狅細
 * - 褰撳墠涓婚摼宸茬粡寮€濮嬮€愭闃舵鍖?
 * - 瑙ｅ瘑鏄湭鏉ユ渶鏈夊彲鑳界嫭绔嬫紨鍖栦负杩滅▼鏈嶅姟鎴栧紓姝ュ鐞嗙幆鑺傜殑閮ㄥ垎
 * - 鍏堟妸鍏ュ彛杈圭晫绋冲畾涓嬫潵锛屽悗缁浛鎹㈠簳灞傚疄鐜版椂褰卞搷鏈€灏?
 *
 * 褰撳墠闃舵绛栫暐锛?
 * - 鍙仛閫忔槑濮旀墭
 * - 涓嶅紩鍏ユ柊琛屼负
 * - 涓嶄慨鏀逛富閾炬帶鍒舵潈
 */
@Service
public class CoordinatingMessageDecryptStage implements MessageDecryptStage {
    // REFACTOR STEP: MESSAGE_ROLE_RENAME

    private final MessageDecryptCoordinator decryptCoordinator;

    public CoordinatingMessageDecryptStage(MessageDecryptCoordinator decryptCoordinator) {
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
