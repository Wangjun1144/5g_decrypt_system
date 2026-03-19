package com.example.procedure.service;

import com.example.procedure.application.pcap.PcapBatchProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;

/**
 * 旧入口接口的默认兼容适配实现。
 *
 * 这个类本质上不再承担真正的业务处理职责，
 * 它的存在意义只有一个：把仍然依赖旧接口的调用方，
 * 平滑转发到新的应用层入口 PcapBatchProcessor。
 *
 * 为什么这一层还保留：
 * 1. 当前项目仍处于第二阶段渐进重构过程中
 * 2. 仍有旧测试或旧调用方依赖 service 包接口
 * 3. 直接删除会扩大改动面，不符合“小步重构、稳定迁移”的策略
 *
 * 为什么标记为 @Primary：
 * - 旧代码如果按类型注入 PcapBatchProcessingService，
 *   这里仍然是默认实现。
 *
 * 后续演进方向：
 * - 当调用方全部迁移到 application.pcap.PcapBatchProcessor 后，
 *   这个类可以被删除。
 */
@Deprecated
@Service
@Primary
public class DefaultPcapBatchProcessingService implements PcapBatchProcessingService {

    /**
     * 新的主入口接口。
     *
     * 这里依赖接口而不是具体实现类 PcapBatchOrchestrator，
     * 是为了让“应用入口边界”比“具体实现类”更稳定。
     * 这对后续继续重构、替换实现、甚至演化为不同运行模式都更友好。
     */
    private final PcapBatchProcessor delegate;

    public DefaultPcapBatchProcessingService(PcapBatchProcessor delegate) {
        this.delegate = delegate;
    }

    /**
     * 将旧入口调用透明转发到新入口。
     *
     * 当前阶段这里故意不添加任何新业务逻辑，
     * 避免兼容层再次变厚。
     */
    @Override
    public void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception {
        delegate.process(pcap, wanted, enabledRaw);
    }
}
