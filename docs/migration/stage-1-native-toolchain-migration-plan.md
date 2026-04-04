# 阶段一迁移设计：外部 EXE 工具链向项目内实现演进

## 1. 文档目的

本文档用于落地“阶段一”迁移工作：先在当前项目中明确边界、统一抽象、固定切换点，
为后续逐步替代以下外部本地技术栈做准备：

- `tshark.exe`
- `text2pcap.exe`
- `libkey_derivation_jni_win.dll`（非 exe，但属于本地原生依赖）

阶段一不直接删除旧实现，也不强行一次性改主链路，而是先把“能力接口”和“迁移路径”
设计稳定，让后续新增 native 实现时不需要继续修改上层业务流程。

## 2. 当前项目中的外部依赖现状

### 2.1 当前已识别的本地外部能力

1. `tshark.exe`
   - 主调用实现：`com.example.procedure.infrastructure.decode.TsharkDecodeJsonTool`
   - 作用：`pcap/pcapng -> tshark json/json+hex`

2. `text2pcap.exe`
   - 主调用实现：`com.example.procedure.infrastructure.decode.Text2PcapBuildTool`
   - 作用：`plaintext hex/hexdump -> pcap`

3. `libkey_derivation_jni_win.dll`
   - 加载入口：`com.example.procedure.keyderivation.KeyDerivationNative`
   - 服务入口：`com.example.procedure.infrastructure.security.keyderivation.NativeKeyDerivationService`
   - 作用：本地 JNI 密钥派生

### 2.2 当前链路结构

当前解码主路径大致可分为两条：

1. pcap 输入链路
   - `PcapDecodeGateway`
   - `TsharkDecodeJsonTool`
   - `StreamingSignalingMessageParser`
   - `SignalingMessage` 后续处理链

2. plaintext/hex 输入链路
   - `HexToJsonDecodeService`
   - `PcapBuildGateway`
   - `Text2PcapBuildTool`
   - `PcapJsonDecodeGateway`
   - `TsharkDecodeJsonTool`

这说明当前系统的“协议解析能力”并不在 Java 内部，而是在外部 Wireshark 工具链中。

## 3. 阶段一的目标与非目标

### 3.1 阶段一目标

阶段一只做下面四件事：

1. 固定迁移边界
2. 统一内部能力抽象
3. 明确新旧实现并存方式
4. 给阶段二及以后预留稳定扩展点

### 3.2 阶段一非目标

阶段一不做下面这些事：

- 不移除 `tshark.exe`
- 不移除 `text2pcap.exe`
- 不替换 JNI `dll`
- 不重写协议解析逻辑
- 不改动现有业务处理主流程的语义

换句话说，阶段一是“收口与定型”，不是“完全替换”。

## 4. 迁移设计原则

### 4.1 先并存，再切换

在 native 实现完成前，旧链路继续作为生产可用实现保留。
后续切换必须支持：

- `external`：仅旧实现
- `hybrid`：新旧并存、对照验证
- `native`：优先内部实现

### 4.2 上层业务只依赖端口，不依赖工具形态

应用层和处理层只关心“输入是什么、输出是什么”，不关心底层到底是：

- 外部 exe
- JNI
- 纯 Java native 实现

### 4.3 新能力不要继续围绕 tshark JSON 建模

旧链路允许继续产出 tshark JSON，但后续新增 native 实现时，内部模型应优先面向：

- `CapturedPacket`
- `DecodedPacket`
- `DecodedMessageCandidate`
- `SignalingMessage`

而不是继续把 tshark JSON 当作长期稳定协议。

### 4.4 迁移必须可回归

后续每个阶段都必须可以做以下对比：

- 输入样本对比
- 产出消息数对比
- 关键字段对比
- 绑定与流程识别结果对比

## 5. 阶段一后的目标架构

### 5.1 建议的能力分层

建议把当前“调用外部工具”重构为以下稳定能力层：

1. Capture Build
   - 把 plaintext/hex 变成 `pcap`

2. Capture Read
   - 把 `pcap/pcapng` 读取成统一 packet 流

3. Protocol Decode
   - 把 packet 解析成项目内部中间模型

4. Message Assemble
   - 把中间模型转成 `SignalingMessage`

5. Security Derivation
   - 提供密钥派生能力，不暴露 JNI 细节

### 5.2 目标依赖方向

目标依赖方向如下：

- `application` / `processing`
  - 依赖稳定 port
- `infrastructure`
  - 提供 `external` 与 `native` 两类实现
- 具体 exe/JNI
  - 只允许留在 infrastructure 的适配器实现里

## 6. 统一能力抽象方案

### 6.1 Capture Build Port

职责：
- 把 plaintext hex / hexdump 变成 pcap 文件

建议保留现有 `PcapBuildTool` / `PcapBuildGateway` 抽象，
后续新增纯 Java 实现即可。

建议演进方向：

- 现有实现
  - `Text2PcapBuildTool`
- 后续新增实现
  - `NativePcapBuildTool`

阶段一结论：
- 这一层抽象已经基本具备，不需要推翻
- 后续重点是把“实现选择机制”做清楚

### 6.2 Capture Read Port

职责：
- 把 `pcap/pcapng` 读取为统一 packet 模型

当前现状：
- 当前项目没有独立的 `capture reader` 抽象
- 读取能力被 `tshark` 外包了

建议新增稳定抽象：

```text
CaptureReader
  -> read(Path capture, Consumer<CapturedPacket> consumer)
```

建议的内部模型：

```text
CapturedPacket
  - packetIndex
  - timestamp
  - linkType
  - rawBytes
  - sourceFile
```

阶段一结论：
- 先把这个抽象写进设计，不急着立刻编码
- 后续替代 `tshark` 时，这是最先需要补的层

### 6.3 Protocol Decode Port

职责：
- 把 packet 或 payload 解成统一的内部解码结果

当前现状：
- `tshark` 直接产出 JSON
- Java 侧再从 JSON 中提取消息

建议目标抽象：

```text
PacketDecodePort
  -> decode(CapturedPacket packet): DecodedPacket
```

建议内部模型：

```text
DecodedPacket
  - packetIndex
  - linkType
  - protocolFamily
  - channel
  - direction
  - decodedFields
  - rawPayloadRefs
```

阶段一结论：
- 不要求现阶段立即实现
- 但后续所有 native 协议实现都应围绕这个模型，而不是 tshark JSON

### 6.4 Message Assemble Port

职责：
- 把协议解码结果统一组装成 `SignalingMessage`

当前现状：
- 逻辑主要散落在现有 parser / bridge 链路中

建议方向：
- 保持现有 `SignalingMessage` 为上层稳定对象
- 后续新增一层 assembler，把 `DecodedPacket` 或 `DecodedMessageCandidate`
  转为 `SignalingMessage`

阶段一结论：
- 上层业务对象不需要推翻
- 重构重点是把“中间模型 -> SignalingMessage”独立出来

### 6.5 Key Derivation Port

职责：
- 给业务层提供密钥派生能力，不泄漏 JNI 绑定细节

当前现状：
- `KeyDerivationService` 已经起到了较好的稳定端口作用

阶段一结论：
- 这层抽象已经基本合理
- JNI 替换不应与 Wireshark 替换绑定在同一个早期阶段

## 7. 新旧实现并存方案

### 7.1 建议的实现分类

建议在概念上把实现分成两组：

1. external adapters
   - `TsharkDecodeJsonTool`
   - `Text2PcapBuildTool`
   - 当前 JNI 适配

2. native adapters
   - 后续 `NativePcapBuildTool`
   - 后续 `PcapFileReader` / `PcapNgFileReader`
   - 后续 `Nas5gsDecoder` / `NrRrcDecoder` / `NgapDecoder`
   - 后续纯 Java `KeyDerivationService`

### 7.2 建议的切换方式

建议未来增加统一配置，例如：

```properties
decode.pipeline.mode=external
decode.capture-build.mode=external
decode.capture-read.mode=external
decode.protocol.mode=external
security.key-derivation.mode=jni
```

推荐值语义：

- `external`
  - 使用当前外部依赖实现
- `native`
  - 使用项目内实现
- `hybrid`
  - 同时执行或对照验证

阶段一说明：
- 本阶段先确定配置策略
- 不强行新增全部配置项
- 真正落地时按阶段增量加入

## 8. 对现有代码的映射关系

### 8.1 保留并继续复用的抽象

以下抽象阶段一不建议推翻：

- `PcapBuildTool`
- `PcapBuildGateway`
- `DecodeJsonTool`
- `PcapDecodeGateway`
- `KeyDerivationService`

原因：
- 这些接口已经在一定程度上把上层业务与底层实现隔开
- 阶段一的重点应是补缺口，不是大面积重命名

### 8.2 当前缺失、后续需要新增的抽象

建议新增但暂不急于编码的抽象：

- `CaptureReader`
- `CapturedPacket`
- `PacketDecodePort`
- `DecodedPacket`
- `DecodedMessageAssembler`

### 8.3 当前需要避免继续扩散的外部依赖

以下依赖不应继续扩散到更多业务类中：

- `ProcessBuilder`
- `System.loadLibrary(...)`
- tshark JSON 结构细节
- Wireshark profile / `user_dlts` 语义

如果新增功能必须暂时使用旧链路，也应通过已有适配器进入，不要直接在新业务类里继续起进程。

## 9. 阶段一完成标准

满足以下条件即可视为阶段一完成：

1. 团队对迁移边界有统一认识
2. 上层业务未来只依赖稳定端口这一原则已明确
3. 已明确哪些抽象保留、哪些抽象后续新增
4. 已明确新旧实现并存策略
5. 后续阶段可以直接按文档拆任务实施

## 10. 阶段二建议起点

阶段二建议从最容易替代的能力入手：

1. 先实现纯 Java `NativePcapBuildTool`
2. 保留 `Text2PcapBuildTool` 作为 fallback
3. 用现有 plaintext 解码相关集成测试做回归

原因：
- `text2pcap.exe` 替代难度最低
- 不涉及复杂协议字段提取
- 可以先验证“内部实现 + 外部链路并存”的迁移模式是否顺畅

## 11. 风险记录

### 11.1 最大风险不是 pcap 文件格式，而是协议解码

真正难点不在于：
- 写 pcap
- 读 pcap

而在于：
- RRC/NAS/NGAP 等协议的深层字段解释
- 与当前 tshark 产物保持足够一致

### 11.2 不建议过早替换 JNI 密钥派生

原因：
- 算法正确性要求高
- 与 Wireshark 迁移不是同一风险类型
- 并行推进会明显增加排障成本

## 12. 后续实施建议

后续建议按以下顺序推进：

1. 阶段二：纯 Java 替代 `text2pcap.exe`
2. 阶段三：新增 `CaptureReader`，开始具备内部读取 `pcap/pcapng` 的能力
3. 阶段四：建立内部协议中间模型
4. 阶段五：按协议优先级逐步替代 `tshark.exe`
5. 阶段六：最后评估 JNI 密钥派生是否迁移

---

本文档是阶段一的落地产物，目标是先把迁移工程变成一个边界清晰、可分阶段执行、
可双轨验证的项目内方案，而不是一次性重写整套外部工具链。
