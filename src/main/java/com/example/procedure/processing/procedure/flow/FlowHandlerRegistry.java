package com.example.procedure.processing.procedure.flow;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 正式的流程 handler 注册入口。
 *
 * 当前定位：
 * 1. 这是 processing.procedure 子域里的 flow handler 正式注册边界
 * 2. 当前仍复用旧的 FlowHandler 接口和实现，避免一次性迁移过大
 * 3. 后续可以继续把 FlowHandler 等类型逐步收口到同一子域包中
 */
@Component
public class FlowHandlerRegistry {

    /**
     * 当前已注册的流程 handler 列表。
     */
    // REFACTOR STEP: FLOW_RUNTIME_REORG
    private final List<FlowHandler> handlers;

    /**
     * 构造正式流程 handler 注册入口。
     *
     * @param handlers 当前所有 handler 实现
     */
    public FlowHandlerRegistry(List<FlowHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 获取当前已注册的 handler 列表。
     *
     * @return handler 列表
     */
    public List<FlowHandler> handlers() {
        return handlers;
    }
}
