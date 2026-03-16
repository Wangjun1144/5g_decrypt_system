package com.example.procedure.context;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UE 上下文更新分发器
 *
 * 职责：
 * 1. 收集所有 UeContextUpdater
 * 2. 根据消息类型匹配 updater
 * 3. 将更新逻辑从 UEContextService 中剥离出去
 */
@Component
public class UeContextUpdateDispatcher {

    private final List<UeContextUpdater> updaters;

    public UeContextUpdateDispatcher(List<UeContextUpdater> updaters) {
        this.updaters = updaters;
    }

    public void dispatch(SignalingMessage msg, UEContext ctx, String procedureId, UeContextUpdateSupport support) {
        for (UeContextUpdater updater : updaters) {
            if (updater.supports(msg)) {
                updater.update(msg, ctx, procedureId, support);
                return;
            }
        }
    }
}