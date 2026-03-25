package com.example.procedure.wireshark;

import com.example.procedure.infrastructure.decode.DecodeJsonTool;

/**
 * @deprecated 旧的 pcap JSON 解码器命名。
 *
 * 当前保留原因：
 * 1. 旧代码可能仍然依赖 wireshark.PcapJsonDecoder 这个名字
 * 2. 新的正式基础设施边界已经迁到 infrastructure.decode.DecodeJsonTool
 * 3. 这里保留为兼容接口，避免一次性修改所有调用方
 */
@Deprecated
// REFACTOR STEP: PACKAGE_REORG_INFRA_DECODE
public interface PcapJsonDecoder extends DecodeJsonTool {
}
