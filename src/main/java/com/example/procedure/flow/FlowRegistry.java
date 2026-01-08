package com.example.procedure.flow;

import com.example.procedure.flow.impl.InitialAccessFlowHandler;
import com.example.procedure.flow.impl.XnHandoverFlowHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlowRegistry {

    private final List<FlowHandler> handlers;

    public FlowRegistry() {
        // 如果你想用 Spring 注入 List<FlowHandler> 也行，这里先用显式注册最直观
        this.handlers = List.of(
                new InitialAccessFlowHandler(),
                new XnHandoverFlowHandler()
        );
    }

    public List<FlowHandler> handlers() {
        return handlers;
    }
}
