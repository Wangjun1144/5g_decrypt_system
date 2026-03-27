package com.example.procedure.infrastructure.decode.bridge.reentry;

import com.example.procedure.model.SignalingMessage;

import java.util.function.Consumer;

public interface DecryptResultReentryService {

    void reenter(SignalingMessage encryptedMsg,
                 Consumer<SignalingMessage> reparsedConsumer) throws Exception;
}
