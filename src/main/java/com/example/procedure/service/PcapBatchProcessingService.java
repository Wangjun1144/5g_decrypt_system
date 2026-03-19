package com.example.procedure.service;

import java.nio.file.Path;
import java.util.Set;

/**
 * 旧的 pcap 批处理兼容接口。
 *
 * 这个接口继续保留的原因不是因为它仍然是推荐入口，
 * 而是为了降低第二阶段重构的迁移风险，给旧代码保留一个稳定适配点。
 *
 * 使用建议：
 * - 新代码不要再优先依赖这个接口。
 * - 新代码应优先依赖 application.pcap 包下的 PcapBatchProcessor。
 * - 这个接口未来的角色应该逐步收缩为“兼容旧调用方”的过渡层。
 *
 * 重构策略说明：
 * - 当前阶段不直接删除旧接口，避免引发大面积连锁改动。
 * - 等调用方逐步迁移完成后，再考虑彻底移除。
 */
@Deprecated
public interface PcapBatchProcessingService {

    void process(Path pcap, Set<String> wanted, Set<String> enabledRaw) throws Exception;
}
