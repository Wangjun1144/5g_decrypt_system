package com.example.procedure.processing.procedure;

import com.example.procedure.model.ProcedureMatchResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.service.ProClassify_Service;
import org.springframework.stereotype.Service;

/**
 * 流程识别服务。
 *
 * 当前阶段定位：
 * - 这是“流程处理阶段”中的识别组件
 * - 它负责把消息交给现有流程识别实现
 * - 暂不改变原有识别规则和判定结果
 *
 * 与旧实现的关系：
 * - 当前仍复用 ProClassify_Service
 * - 这里的主要价值是统一命名和稳定调用边界
 *
 * 后续演进方向：
 * - 可以继续把旧识别逻辑逐步迁移到 processing.procedure 包内
 * - 也可以在未来拆成独立的流程识别微服务
 */
@Service
public class ProcedureRecognitionService {

    private final ProClassify_Service delegate;

    public ProcedureRecognitionService(ProClassify_Service delegate) {
        this.delegate = delegate;
    }

    public ProcedureMatchResult recognize(SignalingMessage msg) {
        return delegate.handleMessage(msg);
    }
}
