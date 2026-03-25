package com.example.procedure.wireshark;

import com.example.procedure.infrastructure.decode.PcapBuildTool;

/**
 * @deprecated 旧的 pcap 构建器命名。
 *
 * 当前保留原因：
 * 1. 旧代码可能仍然依赖 wireshark.PcapBuilder 这个名字
 * 2. 新的正式基础设施边界已经迁到 infrastructure.decode.PcapBuildTool
 * 3. 这里保留为兼容接口，避免一次性修改所有调用方
 */
@Deprecated
// REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
public interface PcapBuilder extends PcapBuildTool {
}
