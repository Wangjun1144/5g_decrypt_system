package com.example.procedure.flow;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FlowHandler 注册表
 *
 * 重构后：
 * - 由 Spring 自动注入所有 FlowHandler 实现
 * - handler 顺序可由 @Order 控制
 */
@Component
public class FlowRegistry {

    private final List<FlowHandler> handlers;

    public FlowRegistry(List<FlowHandler> handlers) {
        this.handlers = handlers;
    }

    public List<FlowHandler> handlers() {
        return handlers;
    }
}