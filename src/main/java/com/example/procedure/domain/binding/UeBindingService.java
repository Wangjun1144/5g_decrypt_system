package com.example.procedure.domain.binding;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.rule.UeIdBinder;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 阶段 1 兼容壳：
 * 用更清晰的业务命名承接旧 UeIdBinder。
 *
 * 阶段 2 再拆成：
 * - BindingResolver
 * - BindingStateStore
 * - PendingBindingStore
 * - BindingFlushCoordinator
 */
@Service
public class UeBindingService {

    private final UeIdBinder delegate;

    public UeBindingService(UeIdBinder delegate) {
        this.delegate = delegate;
    }

    public void handle(SignalingMessage msg, Consumer<SignalingMessage> downstream) {
        delegate.handle(msg, downstream);
    }
}